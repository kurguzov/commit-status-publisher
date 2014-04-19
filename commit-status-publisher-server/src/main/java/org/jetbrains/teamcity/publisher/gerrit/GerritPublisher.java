package org.jetbrains.teamcity.publisher.gerrit;

import com.intellij.openapi.util.Pair;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.Branch;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.teamcity.publisher.BaseCommitStatusPublisher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

public class GerritPublisher extends BaseCommitStatusPublisher {

  private final ServerSshKeyManager mySshKeyManager;

  public GerritPublisher(@Nullable ServerSshKeyManager sshKeyManager,
                         @NotNull Map<String, String> params) {
    super(params);
    mySshKeyManager = sshKeyManager;
  }

  @Override
  public void buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
    Branch branch = build.getBranch();
    if (branch == null || branch.isDefaultBranch())
      return;

    String vote = build.getBuildStatus().isSuccessful() ? getSuccessVote() : getFailureVote();
    String msg = build.getBuildStatus().isSuccessful() ? "Successful build" : "Failed build";

    StringBuilder command = new StringBuilder();
    command.append("gerrit review --project ").append(getGerritProject())
           .append(" --verified ").append(vote)
           .append(" -m \"").append(msg).append("\" ")
           .append(revision.getRevision());
    try {
      runCommand(build.getBuildType().getProject(), command.toString());
    } catch (Exception e) {
      Loggers.SERVER.error("Error while running gerrit command '" + command + "'", e);
      String problemId = "gerrit.publisher." + revision.getRoot().getId();
      build.addBuildProblem(BuildProblemData.createBuildProblem(problemId, "gerrit.publisher", e.getMessage()));
    }
  }

  private void runCommand(@NotNull SProject project, @NotNull String command) throws JSchException, IOException {
    ChannelExec channel = null;
    Session session = null;
    try {
      JSch jsch = new JSch();
      addKeys(jsch, project);
		String gerritServer = getGerritServer();
		Pair<String, Integer> gerritServerPair = extractPort(gerritServer, 29418);
		session = jsch.getSession(getUsername(), gerritServerPair.getFirst(), gerritServerPair.getSecond());
      session.setConfig("StrictHostKeyChecking", "no");
      session.connect();
      channel = (ChannelExec) session.openChannel("exec");
      channel.setCommand(command);
      BufferedReader stderr = new BufferedReader(new InputStreamReader(channel.getErrStream()));
      channel.connect();

      String line;
      StringBuilder details = new StringBuilder();
      while ((line = stderr.readLine()) != null) {
        details.append(line).append("\n");
      }
      if (details.length() > 0)
        throw new IOException(details.toString());
    } finally {
      if (channel != null)
        channel.disconnect();
      if (session != null)
        session.disconnect();
    }
  }

	private Pair<String, Integer> extractPort(String serverString, int defaultPort) {
		String serverName = serverString;
		int serverPort = defaultPort;
		if (serverString.contains(":")) {
			String[] parts = serverString.split(":");
			serverName = parts[0];
			try {
				serverPort = Integer.parseInt(parts[1]);
			} catch (NumberFormatException e) {
			}
		}
		return new Pair<String, Integer>(serverName, serverPort);
	}

  private void addKeys(@NotNull JSch jsch, @NotNull SProject project) throws JSchException {
    String uploadedKeyId = myParams.get(ServerSshKeyManager.TEAMCITY_SSH_KEY_PROP);
    if (uploadedKeyId != null && mySshKeyManager != null) {
      TeamCitySshKey key = mySshKeyManager.getKey(project, uploadedKeyId);
      if (key != null)
        jsch.addIdentity(key.getName(), key.getPrivateKey(), null, null);
    }
    String home = System.getProperty("user.home");
    home = home == null ? new File(".").getAbsolutePath() : new File(home).getAbsolutePath();
    File defaultKey = new File(new File(home, ".ssh"), "id_rsa");
    if (defaultKey.isFile())
      jsch.addIdentity(defaultKey.getAbsolutePath());
  }

  String getGerritServer() {
    return myParams.get("gerritServer");
  }

  String getGerritProject() {
    return myParams.get("gerritProject");
  }

  private String getUsername() {
    return myParams.get("gerritUsername");
  }

  private String getSuccessVote() {
    return myParams.get("successVote");
  }

  private String getFailureVote() {
    return myParams.get("failureVote");
  }
}

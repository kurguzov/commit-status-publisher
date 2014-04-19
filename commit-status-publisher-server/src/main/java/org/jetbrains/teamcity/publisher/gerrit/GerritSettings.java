package org.jetbrains.teamcity.publisher.gerrit;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.RootUrlHolder;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.teamcity.publisher.CommitStatusPublisherSettings;

import java.util.*;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.ssh.ServerSshKeyManager.TEAMCITY_SSH_KEY_PROP;

public class GerritSettings implements CommitStatusPublisherSettings {

  private final PluginDescriptor myDescriptor;
  private final ExtensionHolder myExtensionHolder;
  private final RootUrlHolder rootUrlHolder;
  private final String[] myMandatoryProperties = new String[] {
          "gerritServer", "gerritProject", "gerritUsername",
          "successVote", "failureVote", TEAMCITY_SSH_KEY_PROP};


  public GerritSettings(@NotNull PluginDescriptor descriptor,
                        @NotNull ExtensionHolder extensionHolder,
						@NotNull RootUrlHolder rootUrlHolder) {
    myDescriptor = descriptor;
    myExtensionHolder = extensionHolder;
	  this.rootUrlHolder = rootUrlHolder;
  }

  @NotNull
  public String getId() {
    return "gerritStatusPublisher";
  }

  @NotNull
  public String getName() {
    return "Gerrit";
  }

  @Nullable
  public String getEditSettingsUrl() {
    return myDescriptor.getPluginResourcesPath("gerrit/gerritSettings.jsp");
  }

  @Nullable
  public Map<String, String> getDefaultParameters() {
    Map<String, String> params = new HashMap<String, String>();
    params.put("successVote", "+1");
    params.put("failureVote", "-1");
    return params;
  }

  @Nullable
  public GerritPublisher createPublisher(@NotNull Map<String, String> params) {
    Collection<ServerSshKeyManager> extensions = myExtensionHolder.getExtensions(ServerSshKeyManager.class);
    if (extensions.isEmpty()) {
      return new GerritPublisher(null, params, rootUrlHolder.getRootUrl());
    } else {
      return new GerritPublisher(extensions.iterator().next(), params, rootUrlHolder.getRootUrl());
    }
  }

  @NotNull
  public String describeParameters(@NotNull Map<String, String> params) {
    GerritPublisher publisher = createPublisher(params);
    return "Gerrit " + publisher.getGerritServer() + "/" + publisher.getGerritProject();
  }

  @Nullable
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        for (String mandatoryParam : myMandatoryProperties) {
          if (params.get(mandatoryParam) == null)
            errors.add(new InvalidProperty(mandatoryParam, "must be specified"));
        }
        return errors;
      }
    };
  }
}

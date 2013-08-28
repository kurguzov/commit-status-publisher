package org.jetbrains.teamcity.publisher;

import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CommitStatusPublisherFeature extends BuildFeature {

  private final CommitStatusPublisherFeatureController myController;
  private final PublisherManager myPublisherManager;

  public CommitStatusPublisherFeature(@NotNull CommitStatusPublisherFeatureController controller,
                                      @NotNull PublisherManager publisherManager) {
    myController = controller;
    myPublisherManager = publisherManager;
  }

  @NotNull
  @Override
  public String getType() {
    return "commit-status-publisher";
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Commit status publisher";
  }

  @Nullable
  @Override
  public String getEditParametersUrl() {
    return myController.getUrl();
  }

  @Override
  public boolean isMultipleFeaturesPerBuildTypeAllowed() {
    return true;
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull Map<String, String> params) {
    String publisherId = params.get("publisherId");
    if (publisherId == null)
      return "";
    CommitStatusPublisherSettings settings = myPublisherManager.findSettings(publisherId);
    if (settings == null)
      return "";
    return settings.describeParameters(params);
  }

  @Nullable
  @Override
  public PropertiesProcessor getParametersProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> params) {
        List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
        String voterId = params.get("publisherId");
        if (DummyPublisherSettings.ID.equals(voterId)) {
          errors.add(new InvalidProperty("publisherId", "Select a publisher"));
          return errors;
        }
        CommitStatusPublisherSettings settings = myPublisherManager.findSettings(voterId);
        if (settings == null)
          return errors;
        PropertiesProcessor proc = settings.getParametersProcessor();
        if (proc != null)
          errors.addAll(proc.process(params));
        return errors;
      }
    };
  }
}
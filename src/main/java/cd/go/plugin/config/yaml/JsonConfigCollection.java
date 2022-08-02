package cd.go.plugin.config.yaml;

import com.google.gson.*;

import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import cd.go.plugin.config.yaml.HashUtils;

public class JsonConfigCollection {
    private static final int DEFAULT_VERSION = 1;
    private final Gson gson;

    private Set<Integer> uniqueVersions = new HashSet<>();
    private JsonObject mainObject = new JsonObject();
    private JsonArray environments = new JsonArray();
    private JsonArray pipelines = new JsonArray();
    private JsonArray errors = new JsonArray();
    private Map<String, String> pipelineEnvironmentMap = new HashMap<String, String>();


    public JsonConfigCollection() {
        gson = new Gson();

        updateTargetVersionTo(DEFAULT_VERSION);
        mainObject.add("environments", environments);
        mainObject.add("pipelines", pipelines);
        mainObject.add("errors", errors);
    }

    public void addToPipelineEnvironmentMap(String pipeline, String environment) {
        pipelineEnvironmentMap.put(pipeline, environment);
    }
    protected Map<String, String> getPipelineEnvironmentMap() {
        return pipelineEnvironmentMap;
    }
    public String getEnvironmentFromMap(String pipeline) {
        return pipelineEnvironmentMap.get(pipeline);
    }
    public List<String> getPipelinesFromMap(String environment) {
        List<String> l = new ArrayList<String>();
        for (Map.Entry<String, String> entry : pipelineEnvironmentMap.entrySet()) {
            if (entry.getValue().equals(environment)) {
                l.add(entry.getKey());
            }
        }
        return l;
    }

    protected JsonArray getEnvironments() {
        return environments;
    }

    public void addEnvironment(JsonElement environment, String location) {
        // We want to go back through the envJson now to fix up our pipelines to be uniquely named
        JsonArray t = environment.getAsJsonObject().getAsJsonArray("pipelines");
        Iterator<JsonElement> it = t.iterator();
        String envName = environment.getAsJsonObject().getAsJsonPrimitive("name").getAsString();

        JsonArray newA = new JsonArray();
        while (it.hasNext()) {
            String pipelineName = it.next().getAsString();
            newA.add(HashUtils.randomizePipelineName(envName, pipelineName));
        }
        environment.getAsJsonObject().remove("pipelines");
        environment.getAsJsonObject().add("pipelines", newA);

        environments.add(environment);
        environment.getAsJsonObject().add("location", new JsonPrimitive(location));
    }

    public JsonObject getJsonObject() {
        return mainObject;
    }

    public void addPipeline(JsonElement pipeline, String location) {
        String pipelineName = pipeline.getAsJsonObject().getAsJsonPrimitive("name").getAsString();
        String envName = getEnvironmentFromMap(pipelineName);

        pipeline.getAsJsonObject().remove("name");
        pipeline.getAsJsonObject().addProperty("name", HashUtils.randomizePipelineName(envName, pipelineName));
        pipelines.add(pipeline);
        pipeline.getAsJsonObject().add("location", new JsonPrimitive(location));
    }

    public JsonArray getPipelines() {
        return pipelines;
    }

    public JsonArray getErrors() {
        return errors;
    }

    public void addError(String message, String location) {
        this.addError(new PluginError(message, location));
    }

    public void addError(PluginError error) {
        errors.add(gson.toJsonTree(error));
    }

    public void append(JsonConfigCollection other) {
        this.environments.addAll(other.environments);
        this.pipelines.addAll(other.pipelines);
        this.errors.addAll(other.errors);
        this.uniqueVersions.addAll(other.uniqueVersions);
    }

    public void updateFormatVersionFound(int version) {
        uniqueVersions.add(version);
        updateTargetVersionTo(version);
    }

    public void updateTargetVersionFromFiles() {
        if (uniqueVersions.size() > 1) {
            throw new RuntimeException("Versions across files are not unique. Found versions: " + uniqueVersions + ". There can only be one version across the whole repository.");
        }
        updateTargetVersionTo(uniqueVersions.iterator().hasNext() ? uniqueVersions.iterator().next() : DEFAULT_VERSION);
    }

    private void updateTargetVersionTo(int targetVersion) {
        mainObject.remove("target_version");
        mainObject.add("target_version", new JsonPrimitive(targetVersion));
    }
}
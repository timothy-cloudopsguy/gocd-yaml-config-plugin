package cd.go.plugin.config.yaml.transforms;

import cd.go.plugin.config.yaml.GitHelper;
import cd.go.plugin.config.yaml.JsonConfigCollection;
import cd.go.plugin.config.yaml.PluginError;
import cd.go.plugin.config.yaml.YamlConfigException;
import cd.go.plugin.config.yaml.YamlUtils;
import com.google.gson.JsonElement;
import com.google.gson.internal.LinkedTreeMap;

import java.util.Map;
import java.util.List;

public class RootTransform {
    private PipelineTransform pipelineTransform;
    private EnvironmentsTransform environmentsTransform;

    public RootTransform() {
        EnvironmentVariablesTransform environmentVarsTransform = new EnvironmentVariablesTransform();
        MaterialTransform material = new MaterialTransform();
        ParameterTransform parameterTransform = new ParameterTransform();
        JobTransform job = new JobTransform(environmentVarsTransform, new TaskTransform());
        StageTransform stage = new StageTransform(environmentVarsTransform, job);
        this.pipelineTransform = new PipelineTransform(material, stage, environmentVarsTransform, parameterTransform);
        this.environmentsTransform = new EnvironmentsTransform(environmentVarsTransform);
    }

    public RootTransform(GitHelper gitHelper) {
        EnvironmentVariablesTransform environmentVarsTransform = new EnvironmentVariablesTransform();
        MaterialTransform material = new MaterialTransform();
        ParameterTransform parameterTransform = new ParameterTransform();
        JobTransform job = new JobTransform(environmentVarsTransform, new TaskTransform());
        StageTransform stage = new StageTransform(environmentVarsTransform, job);
        this.pipelineTransform = new PipelineTransform(gitHelper, material, stage, environmentVarsTransform, parameterTransform);
        this.environmentsTransform = new EnvironmentsTransform(environmentVarsTransform);
    }

    public RootTransform(PipelineTransform pipelineTransform, EnvironmentsTransform environmentsTransform) {
        this.pipelineTransform = pipelineTransform;
        this.environmentsTransform = environmentsTransform;
    }

    public String inverseTransformPipeline(Map<String, Object> pipeline) {
        Map<String, Object> result = new LinkedTreeMap<>();
        result.put("format_version", 10);
        result.put("pipelines", pipelineTransform.inverseTransform(pipeline));
        return YamlUtils.dump(result);
    }

    public JsonConfigCollection transform(Object rootObj, String location) {
        JsonConfigCollection partialConfig = new JsonConfigCollection();
        Map<String, Object> rootMap = (Map<String, Object>) rootObj;
        // must obtain format_version first
        int formatVersion = 1;
        for (Map.Entry<String, Object> pe : rootMap.entrySet()) {
            if ("format_version".equalsIgnoreCase(pe.getKey())) {
                formatVersion = Integer.valueOf((String) pe.getValue());
                partialConfig.updateFormatVersionFound(formatVersion);
            } else if ("environments".equalsIgnoreCase(pe.getKey())) {
                if (pe.getValue() == null)
                    continue;
                Map<String, Object> environments = (Map<String, Object>) pe.getValue();
                for (Map.Entry<String, Object> env : environments.entrySet()) {
                    String envName = env.getKey();
                    Object envObj = env.getValue();
                    if (envObj == null)
                        continue;
                    if (!(envObj instanceof Map))
                        throw new YamlConfigException("Expected environment to be a hash");
                    Map<String, Object> envMap = (Map<String, Object>) envObj;
                    Object value = envMap.get("pipelines");

                    if (value != null) {
                        List<String> list = (List<String>) value;
                        if (list.size() == 0)
                            continue;
                        for (String item : list) {
                            partialConfig.addToPipelineEnvironmentMap(item, envName);
                        }
                    }
                
                }
            }
        }
        for (Map.Entry<String, Object> pe : rootMap.entrySet()) {
            if ("pipelines".equalsIgnoreCase(pe.getKey())) {
                if (pe.getValue() == null)
                    continue;
                Map<String, Object> pipelines = (Map<String, Object>) pe.getValue();
                for (Map.Entry<String, Object> pipe : pipelines.entrySet()) {
                    try {
                        JsonElement jsonPipeline = pipelineTransform.transform(pipe, formatVersion);
                        partialConfig.addPipeline(jsonPipeline, location);
                    } catch (Exception ex) {
                        partialConfig.addError(new PluginError(
                                String.format("Failed to parse pipeline %s; %s", pipe.getKey(), ex.getMessage()), location));
                    }
                }
            } else if ("environments".equalsIgnoreCase(pe.getKey())) {
                if (pe.getValue() == null)
                    continue;
                Map<String, Object> environments = (Map<String, Object>) pe.getValue();
                for (Map.Entry<String, Object> env : environments.entrySet()) {
                    try {
                        JsonElement jsonEnvironment = environmentsTransform.transform(env);
                        partialConfig.addEnvironment(jsonEnvironment, location);
                    } catch (Exception ex) {
                        partialConfig.addError(new PluginError(
                                String.format("Failed to parse environment %s; %s", env.getKey(), ex.getMessage()), location));
                    }
                }
            } else if (!"common".equalsIgnoreCase(pe.getKey()) && !"format_version".equalsIgnoreCase(pe.getKey()))
                throw new YamlConfigException(pe.getKey() + " is invalid, expected format_version, pipelines, environments, or common");
        }
        return partialConfig;
    }
}

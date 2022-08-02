package cd.go.plugin.config.yaml;

import cd.go.plugin.config.yaml.transforms.RootTransform;
import cd.go.plugin.config.yaml.GitHelper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoApiRequest;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Supplier;

import static cd.go.plugin.config.yaml.PluginSettings.DEFAULT_FILE_PATTERN;
import static cd.go.plugin.config.yaml.PluginSettings.DEFAULT_TEMPLATE_REPO;
import static cd.go.plugin.config.yaml.PluginSettings.DEFAULT_TEMPLATE_REPO_BRANCH;
import static cd.go.plugin.config.yaml.PluginSettings.DEFAULT_TEMPLATE_BASE_PATH;
import static cd.go.plugin.config.yaml.PluginSettings.PLUGIN_SETTINGS_FILE_PATTERN;
import static cd.go.plugin.config.yaml.PluginSettings.PLUGIN_SETTINGS_TEMPLATE_REPO;
import static cd.go.plugin.config.yaml.PluginSettings.PLUGIN_SETTINGS_TEMPLATE_REPO_BRANCH;
import static cd.go.plugin.config.yaml.PluginSettings.PLUGIN_SETTINGS_TEMPLATE_BASE_PATH;
import static com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse.*;
import static java.lang.String.format;

import static cd.go.plugin.config.yaml.GitHelper.WORKING_DIR_BASE;

@Extension
public class YamlConfigPlugin implements GoPlugin, ConfigRepoMessages {
    private static final String DISPLAY_NAME_FILE_PATTERN = "Go YAML files pattern";
    private static final String DISPLAY_NAME_TEMPLATE_REPO = "Go YAML template repo";
    private static final String DISPLAY_NAME_TEMPLATE_REPO_BRANCH = "Go YAML template repo branch";
    private static final String DISPLAY_NAME_TEMPLATE_BASE_PATH = "Go YAML template base path";
    private static final String PLUGIN_ID = "yaml.config.plugin";
    private static Logger LOGGER = Logger.getLoggerFor(YamlConfigPlugin.class);

    private final Gson gson = new Gson();
    private GoApplicationAccessor goApplicationAccessor;
    private PluginSettings settings;

    private GitHelper gitHelper;

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
        this.goApplicationAccessor = goApplicationAccessor;
        ensureConfigured();

        // Map<String, String> env = System.getenv();
        // LOGGER.info("********************************************");
        // LOGGER.info("********************************************");
        // LOGGER.info("********************************************");
        // LOGGER.info("********************************************");
        // LOGGER.info("********************************************");
        // LOGGER.info("********************************************");
        // env.forEach((k, v) -> LOGGER.info(k + ":" + v));
        // LOGGER.info("********************************************");
        // LOGGER.info("********************************************");
        // LOGGER.info("********************************************");
        // LOGGER.info("********************************************");
        // LOGGER.info("********************************************");
        // LOGGER.info("********************************************");
        // this.workingDir = (env.get(WRAPPER_WORKING_DIR) != null) ? env.get(WRAPPER_WORKING_DIR)+workingDirExtension : "/tmp"+workingDirExtension;
        // this.workingDir = WORKING_DIR_BASE;
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return getGoPluginIdentifier();
    }

    private GoPluginIdentifier getGoPluginIdentifier() {
        return new GoPluginIdentifier("configrepo", Arrays.asList("1.0", "2.0", "3.0"));
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest request) throws UnhandledRequestTypeException {
        String requestName = request.requestName();

        switch (requestName) {
            case REQ_PLUGIN_SETTINGS_GET_CONFIGURATION:
                return handleGetPluginSettingsConfiguration();
            case REQ_CONFIG_FILES:
                return handleGetConfigFiles(request);
            case REQ_PLUGIN_SETTINGS_GET_VIEW:
                try {
                    return handleGetPluginSettingsView();
                } catch (IOException e) {
                    return error(gson.toJson(format("Failed to find template: %s", e.getMessage())));
                }
            case REQ_PLUGIN_SETTINGS_VALIDATE_CONFIGURATION:
                return handleValidatePluginSettingsConfiguration();
            case REQ_PARSE_CONTENT:
                return handleParseContentRequest(request);
            case REQ_PARSE_DIRECTORY:
                ensureConfigured();
                return handleParseDirectoryRequest(request);
            case REQ_PIPELINE_EXPORT:
                return handlePipelineExportRequest(request);
            case REQ_GET_CAPABILITIES:
                return success(gson.toJson(new Capabilities()));
            case REQ_PLUGIN_SETTINGS_CHANGED:
                configurePlugin(PluginSettings.fromJson(request.requestBody()));
                return new DefaultGoPluginApiResponse(SUCCESS_RESPONSE_CODE, "");
            case REQ_GET_ICON:
                return handleGetIconRequest();
            default:
                throw new UnhandledRequestTypeException(requestName);
        }
    }

    private GoPluginApiResponse handleGetIconRequest() {
        try {
            JsonObject jsonObject = new JsonObject();
            byte[] contents = IOUtils.toByteArray(getClass().getResourceAsStream("/yaml.svg"));

            jsonObject.addProperty("content_type", "image/svg+xml");
            jsonObject.addProperty("data", Base64.getEncoder().encodeToString(contents));
            return success(gson.toJson(jsonObject));
        } catch (IOException e) {
            return error(e.getMessage());
        }
    }

    String getFilePattern() {
        if (null != settings && !isBlank(settings.getFilePattern())) {
            return settings.getFilePattern();
        }
        return DEFAULT_FILE_PATTERN;
    }

    String getTemplateRepo() {
        if (null != settings && !isBlank(settings.getTemplateRepo())) {
            return settings.getTemplateRepo();
        }
        return DEFAULT_TEMPLATE_REPO;
    }

    String getTemplateRepoBranch() {
        if (null != settings && !isBlank(settings.getTemplateRepoBranch())) {
            return settings.getTemplateRepoBranch();
        }
        return DEFAULT_TEMPLATE_REPO_BRANCH;
    }

    String getTemplateBasePath() {
        if (null != settings && !isBlank(settings.getTemplateBasePath())) {
            return settings.getTemplateBasePath();
        }
        return DEFAULT_TEMPLATE_BASE_PATH;
    }

    /**
     * fetches plugin settings if we haven't yet
     */
    private void ensureConfigured() {
        if (null == settings) {
            settings = fetchPluginSettings();
        }

        LOGGER.info("Running ensureConfigured()");
        // this.settings = settings;
        try {
            File workDir = new File(WORKING_DIR_BASE + "/" + GitHelper.generateSanitizedRepoName(settings.getTemplateRepo()) + "." + settings.getTemplateRepoBranch());
            if (!workDir.exists()) {
                workDir.mkdirs();
            }
            this.gitHelper = new GitHelper(settings.getTemplateRepo(), settings.getTemplateRepoBranch(), settings.getTemplateBasePath(), workDir);
        } catch (Exception e) {
            LOGGER.error("Error while trying to initialize template repo \n Message: {} \n StackTrace: {}", e.getMessage(), e.getStackTrace(), e);
            throw new RuntimeException(e);
        }

    }

    private GoPluginApiResponse handleParseContentRequest(GoPluginApiRequest request) {
        return handlingErrors(() -> {
            ParsedRequest parsed = ParsedRequest.parse(request);

            YamlConfigParser parser = new YamlConfigParser(gitHelper);
            Map<String, String> contents = parsed.getParam("contents");
            JsonConfigCollection result = new JsonConfigCollection();
            contents.forEach((filename, content) -> {
                parser.parseStream(result, new ByteArrayInputStream(content.getBytes()), filename);
            });
            result.updateTargetVersionFromFiles();

            return success(gson.toJson(result.getJsonObject()));
        });
    }

    private GoPluginApiResponse handlePipelineExportRequest(GoPluginApiRequest request) {
        return handlingErrors(() -> {
            ParsedRequest parsed = ParsedRequest.parse(request);

            Map<String, Object> pipeline = parsed.getParam("pipeline");
            String name = (String) pipeline.get("name");

            Map<String, String> responseMap = Collections.singletonMap("pipeline", new RootTransform().inverseTransformPipeline(pipeline));
            DefaultGoPluginApiResponse response = success(gson.toJson(responseMap));

            response.addResponseHeader("Content-Type", "application/x-yaml; charset=utf-8");
            response.addResponseHeader("X-Export-Filename", name + ".gocd.yaml");
            return response;
        });
    }

    private GoPluginApiResponse handleParseDirectoryRequest(GoPluginApiRequest request) {
        return handlingErrors(() -> {
            ParsedRequest parsed = ParsedRequest.parse(request);
            File baseDir = new File(parsed.getStringParam("directory"));
            String[] files = scanForConfigFiles(parsed, baseDir);

            YamlConfigParser parser = new YamlConfigParser(gitHelper);

            JsonConfigCollection config = parser.parseFiles(baseDir, files);
            config.updateTargetVersionFromFiles();

            return success(gson.toJson(config.getJsonObject()));
        });
    }

    private GoPluginApiResponse handleGetConfigFiles(GoPluginApiRequest request) {
        return handlingErrors(() -> {
            ParsedRequest parsed = ParsedRequest.parse(request);
            File baseDir = new File(parsed.getStringParam("directory"));

            Map<String, String[]> result = new HashMap<>();
            result.put("files", scanForConfigFiles(parsed, baseDir));

            return success(gson.toJson(result));
        });
    }

    private String[] scanForConfigFiles(ParsedRequest parsed, File baseDir) {
        LOGGER.info("scanForConfigFiles()");
        String pattern = parsed.getConfigurationKey(PLUGIN_SETTINGS_FILE_PATTERN);

        if (isBlank(pattern)) {
            pattern = getFilePattern();
        }

        return new AntDirectoryScanner().getFilesMatchingPattern(baseDir, pattern);
    }

    private static boolean isBlank(String pattern) {
        return pattern == null || pattern.isEmpty();
    }

    private GoPluginApiResponse handleGetPluginSettingsView() throws IOException {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("template", IOUtils.toString(getClass().getResourceAsStream("/plugin-settings.template.html"), "UTF-8"));
        return success(gson.toJson(response));
    }

    private GoPluginApiResponse handleValidatePluginSettingsConfiguration() {
        List<Map<String, Object>> response = new ArrayList<>();
        return success(gson.toJson(response));
    }

    private GoPluginApiResponse handleGetPluginSettingsConfiguration() {
        Map<String, Object> response = new HashMap<>();
        response.put(PLUGIN_SETTINGS_FILE_PATTERN, createField(DISPLAY_NAME_FILE_PATTERN, DEFAULT_FILE_PATTERN, false, false, "0"));
        response.put(PLUGIN_SETTINGS_TEMPLATE_REPO, createField(DISPLAY_NAME_TEMPLATE_REPO, DEFAULT_TEMPLATE_REPO, false, false, "1"));
        response.put(PLUGIN_SETTINGS_TEMPLATE_REPO_BRANCH, createField(DISPLAY_NAME_TEMPLATE_REPO_BRANCH, DEFAULT_TEMPLATE_REPO_BRANCH, false, false, "2"));
        response.put(PLUGIN_SETTINGS_TEMPLATE_BASE_PATH, createField(DISPLAY_NAME_TEMPLATE_BASE_PATH, DEFAULT_TEMPLATE_BASE_PATH, false, false, "3"));
        return success(gson.toJson(response));
    }

    private Map<String, Object> createField(String displayName, String defaultValue, boolean isRequired, boolean isSecure, String displayOrder) {
        Map<String, Object> fieldProperties = new HashMap<String, Object>();
        fieldProperties.put("display-name", displayName);
        fieldProperties.put("default-value", defaultValue);
        fieldProperties.put("required", isRequired);
        fieldProperties.put("secure", isSecure);
        fieldProperties.put("display-order", displayOrder);
        return fieldProperties;
    }

    private GoPluginApiResponse handlingErrors(Supplier<GoPluginApiResponse> exec) {
        try {
            return exec.get();
        } catch (ParsedRequest.RequestParseException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred in YAML configuration plugin.", e);
            JsonConfigCollection config = new JsonConfigCollection();
            config.addError(new PluginError(e.toString(), "YAML config plugin"));
            return error(gson.toJson(config.getJsonObject()));
        }
    }

    private PluginSettings fetchPluginSettings() {
        Map<String, Object> requestMap = new HashMap<String, Object>();
        requestMap.put("plugin-id", PLUGIN_ID);
        GoApiResponse response = goApplicationAccessor.submit(createGoApiRequest(REQ_GET_PLUGIN_SETTINGS, JSONUtils.toJSON(requestMap)));

        if (response.responseBody() == null || response.responseBody().trim().isEmpty()) {
            return new PluginSettings();
        }

        return PluginSettings.fromJson(response.responseBody());
    }

    private void configurePlugin(PluginSettings settings) {
        LOGGER.info("Running configurePlugin()");
        this.settings = settings;
        try {
            File workDir = new File(WORKING_DIR_BASE + "/" + GitHelper.generateSanitizedRepoName(settings.getTemplateRepo()) + "." + settings.getTemplateRepoBranch());
            if (!workDir.exists()) {
                workDir.mkdirs();
            }
            this.gitHelper = new GitHelper(settings.getTemplateRepo(), settings.getTemplateRepoBranch(), settings.getTemplateBasePath(), workDir);
        } catch (Exception e) {
            LOGGER.error("Error while trying to initialize template repo \n Message: {} \n StackTrace: {}", e.getMessage(), e.getStackTrace(), e);
            throw new RuntimeException(e);
        }
    }

    private GoApiRequest createGoApiRequest(final String api, final String responseBody) {
        return new GoApiRequest() {
            @Override
            public String api() {
                return api;
            }

            @Override
            public String apiVersion() {
                return "1.0";
            }

            @Override
            public GoPluginIdentifier pluginIdentifier() {
                // return getGoPluginIdentifier();
                return new GoPluginIdentifier("configrepo", Arrays.asList("1.0", "2.0", "3.0"));
            }

            @Override
            public Map<String, String> requestParameters() {
                return null;
            }

            @Override
            public Map<String, String> requestHeaders() {
                return null;
            }

            @Override
            public String requestBody() {
                return responseBody;
            }
        };
    }
}

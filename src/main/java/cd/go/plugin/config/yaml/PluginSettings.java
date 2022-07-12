package cd.go.plugin.config.yaml;

import java.util.Map;

class PluginSettings {
    static final String PLUGIN_SETTINGS_FILE_PATTERN = "file_pattern";
    static final String PLUGIN_SETTINGS_TEMPLATE_REPO = "template_repo";
    static final String PLUGIN_SETTINGS_TEMPLATE_REPO_BRANCH = "template_repo_branch";
    static final String PLUGIN_SETTINGS_TEMPLATE_BASE_PATH = "template_base_path";
    static final String DEFAULT_FILE_PATTERN = "**/*.gocd.yaml,**/*.gocd.yml";
    static final String DEFAULT_TEMPLATE_REPO = "git@bitbucket.org:openalpr/cloudops-gocd-library.git";
    static final String DEFAULT_TEMPLATE_REPO_BRANCH = "develop";
    static final String DEFAULT_TEMPLATE_BASE_PATH = "templates/";

    private String filePattern;
    private String templateRepo;
    private String templateRepoBranch;
    private String templateBasePath;

    PluginSettings() {
        this(DEFAULT_FILE_PATTERN, DEFAULT_TEMPLATE_REPO, DEFAULT_TEMPLATE_REPO_BRANCH, DEFAULT_TEMPLATE_BASE_PATH);
    }

    PluginSettings(String filePattern, String templateRepo, String templateRepoBranch, String templateBasePath) {
        this.filePattern = filePattern;
        this.templateRepo = templateRepo;
        this.templateRepoBranch = templateRepoBranch;
        this.templateBasePath = templateBasePath;
    }

    static PluginSettings fromJson(String json) {
        Map<String, String> raw = JSONUtils.fromJSON(json);
        return new PluginSettings(
            raw.get(PLUGIN_SETTINGS_FILE_PATTERN),
            raw.get(PLUGIN_SETTINGS_TEMPLATE_REPO),
            raw.get(PLUGIN_SETTINGS_TEMPLATE_REPO_BRANCH),
            raw.get(PLUGIN_SETTINGS_TEMPLATE_BASE_PATH));
    }

    String getFilePattern() {
        return filePattern;
    }

    String getTemplateRepo() {
        return templateRepo;
    }

    String getTemplateRepoBranch() {
        return templateRepoBranch;
    }

    String getTemplateBasePath() {
        return templateBasePath;
    }
}
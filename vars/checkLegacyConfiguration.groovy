import com.sap.piper.ConfigurationLoader
import com.sap.cloud.sdk.s4hana.pipeline.Analytics

def call(Map parameters) {
    Script script = parameters.script

    checkNotUsingWhiteSourceOrgToken(script)
    checkRenamedMavenStep(script)
    checkMavenGlobalSettings(script)
    checkCloudfoundryDeployment(script)
    checkNeoDeployment(script)
    checkRenamedBackendIntegrationTests(script)
    convertDebugReportConfig(script)
    checkStaticCodeChecksConfig(script)
    checkSharedConfig(script)
    checkFortify(script)
    checkArtifactSetVersion(script)
    checkAutomaticVersioning(script)
    checkGlobalExtensionConfiguration(script)
    checkTmsUpload(script)
    checkEndToEndTestsAppUrl(script)
}

void checkGlobalExtensionConfiguration(Script script) {
    Map generalConfiguration = loadEffectiveGeneralConfiguration(script: script)
    if (generalConfiguration?.extensionRepository) {
        failWithConfigError("Your pipeline configuration contains the obsolete configuration parameter " +
            "'general.extensionRepository=${generalConfiguration.extensionRepository}'. " +
            "The new configuration parameter in the 'general' section is called 'globalExtensionsRepository'. " +
            "To configure a version please use globalExtensionsVersion. " +
            "You can also configure globalExtensionsRepositoryCredentialsId in case the extension repository is secured. " +
            "Please note that you can also configure these values as part of your custom defaults / shared configuration.")
    }
}

void checkRenamedBackendIntegrationTests(Script script) {
    checkRenamedStage(script, 'integrationTests', 'backendIntegrationTests')
}

void checkRenamedMavenStep(Script script) {
    checkRenamedStep(script, 'executeMaven', 'mavenExecute')
}

void checkMavenGlobalSettings(Script script) {
    Map mavenConfiguration = loadEffectiveStepConfiguration(script: script, stepName: 'mavenExecute')

    // Maven globalSettings obsolete since introduction of DL-Cache
    if (mavenConfiguration?.globalSettingsFile) {
        failWithConfigError("Your pipeline configuration contains the obsolete configuration parameter " +
            "'mavenExecute.globalSettingsFile=${mavenConfiguration.globalSettingsFile}'. " +
            "The SAP Cloud SDK Pipeline uses an own global settings file to inject its download proxy as maven repository mirror. " +
            "Please only specify the settings via the parameter 'projectSettingsFile'")
    }
}

void checkNotUsingWhiteSourceOrgToken(Script script) {
    Map stageConfig = loadEffectiveStageConfiguration(script: script, stageName:'whitesourceScan')
    if (stageConfig?.orgToken) {
        failWithConfigError("Your pipeline configuration may not use 'orgtoken' in whiteSourceScan stage. " +
            "Store it as a 'Secret Text' in Jenkins and use the 'credentialsId' field instead.")
    }
}

void checkCloudfoundryDeployment(Script script) {
    checkRenamedStep(script, 'deployToCfWithCli', 'cloudFoundryDeploy')
}

void checkNeoDeployment(Script script) {
    checkRenamedStep(script, 'deployToNeoWithCli', 'neoDeploy')
}

boolean convertDebugReportConfig(Script script) {
    if (!ConfigurationLoader.postActionConfiguration(script, 'archiveDebugLog'))
        return

    failWithConfigError("The configuration key archiveDebugLog in the postAction configuration may not be used anymore. " +
        "Please use the step configuration for debugReportArchive instead.")
}

void checkStaticCodeChecksConfig(Script script) {
    if (loadEffectiveStageConfiguration(script: script, stageName: 'staticCodeChecks')) {
        failWithConfigError("Your pipeline configuration contains an entry for the stage staticCodeChecks. " +
            "This configuration option was removed in version v32. " +
            "Please migrate the configuration into your pom.xml file or the configuration for the new step mavenExecuteStaticCodeChecks. " +
            "Details can be found in the release notes as well as in the step documentation: https://sap.github.io/jenkins-library/steps/mavenExecuteStaticCodeChecks/.")
    }
}

void checkTmsUpload(Script script) {
    if (loadEffectiveStageConfiguration(script: script, stageName: 'productionDeployment').tmsUpload) {
        failWithConfigError("Your pipeline configuration contains an entry tmsUpload in the stage productionDeployment. " +
            "This configuration option was removed in version v39. " +
            "Please configure the step tmsUpload instead. " +
            "Details can be found in the release notes at https://github.com/SAP/cloud-s4-sdk-pipeline/releases/tag/v39 as well as in the step documentation: https://sap.github.io/jenkins-library/steps/tmsUpload/."
        )
    }
}

void checkSharedConfig(Script script) {
    if (script.commonPipelineEnvironment.configuration.general?.sharedConfiguration) {
        failWithConfigError("Your pipeline configuration contains an entry for the sharedConfiguration in the general section. " +
            "This configuration option has been aligned with Project 'Piper' in version v33. " +
            "Please rename the config key to 'customDefaults' and move it to the root level of the config file, i.e. before the 'general' section. " +
            "The value of this key needs to be a list of strings. " +
            "See also https://sap.github.io/jenkins-library/configuration/#custom-default-configuration for more information." +
            "As an example for the necessary change, please consult the release notes of v33 at https://github.com/SAP/cloud-s4-sdk-pipeline/releases/tag/v33")
    }
}

void checkFortify(Script script){
    checkRenamedStep(script, 'executeFortifyScan', 'fortifyExecuteScan')
    if (ConfigurationLoader.stageConfiguration(script, 'fortifyScan')) {
        failWithConfigError("Your pipeline configuration contains an entry for the stage fortifyScan. " +
            "This configuration option was removed. To configure fortify please use the step configuration for fortifyExecuteScan. " +
            "Details can be found in the documentation: https://sap.github.io/jenkins-library/steps/fortifyExecuteScan/")
    }
}

void checkArtifactSetVersion(Script script){
    // NOTE: Not using the effective configuration here, since that includes defaults
    // and the jenkins-library defaults include a configuration for "artifactSetVersion" which
    // we want to ignore.
    if (ConfigurationLoader.stepConfiguration(script, 'artifactSetVersion')) {
        failWithConfigError("The configuration key artifactSetVersion in the steps configuration may not be used anymore. " +
            "Please use artifactPrepareVersion instead. Details can be found in the release notes of v37 of the pipeline: " +
            "https://github.com/SAP/cloud-s4-sdk-pipeline/releases/tag/v37")
    }
}

void checkAutomaticVersioning(Script script){
    if (ConfigurationLoader.generalConfiguration(script).containsKey('automaticVersioning')) {
        failWithConfigError("The configuration key automaticVersioning in the general configuration may not be used anymore. " +
            "Please configure the artifactPrepareVersion step instead. Details can be found in the release notes of v37 of the pipeline: " +
            "https://github.com/SAP/cloud-s4-sdk-pipeline/releases/tag/v37")
    }
}

void checkEndToEndTestsAppUrl(Script script) {
    Map e2eStageConfig = loadEffectiveStageConfiguration(script: script, stageName: 'endToEndTests')
    Map prodStageConfig = loadEffectiveStageConfiguration(script: script, stageName: 'productionDeployment')

    if (e2eStageConfig?.appUrls instanceof String) {
        failWithConfigError("The configuration key appUrls in the endToEndTests stage configuration must not be a string anymore. " +
            "Please configure appUrls as a list of maps. For example:\n" +
            "appUrls: \n" + "  - url: 'https://my-url.com'\n" + "    credentialId: myCreds\n" +
            "More details can be found in the documentation: https://sap.github.io/jenkins-library/steps/npmExecuteEndToEndTests/#parameters")
    }
    if (prodStageConfig?.appUrls instanceof String) {
        failWithConfigError("The configuration key appUrls in the productionDeployment stage configuration must not be a string anymore. " +
            "Please configure appUrls as a list of maps. For example:\n" +
            "appUrls: \n" + "  - url: 'https://my-url.com'\n" + "    credentialId: myCreds\n" +
            "More details can be found in the documentation: https://sap.github.io/jenkins-library/steps/npmExecuteEndToEndTests/#parameters")
    }

    if (e2eStageConfig?.appUrls) {
        for (int i = 0; i < e2eStageConfig.appUrls.size(); i++) {
            Map appUrl = e2eStageConfig.appUrls[i]
            if (appUrl.parameters instanceof String) {
                failWithConfigError("The configuration key parameters in the endToEndTests stage configuration must not be a string anymore. " +
                    "Please configure parameters as a list of strings. For example:\n" +
                    "appUrls: \n" + "  - url: 'https://my-url.com'\n" + "    parameters: ['--tag', 'scenario1']\n" +
                    "More details can be found in the documentation: https://sap.github.io/jenkins-library/steps/npmExecuteEndToEndTests/#parameters")
            }
        }
    }
    if (prodStageConfig?.appUrls) {
        for (int i = 0; i < prodStageConfig.appUrls.size(); i++) {
            Map appUrl = prodStageConfig.appUrls[i]
            if (appUrl.parameters instanceof String) {
                failWithConfigError("The configuration key parameters in the productionDeployment stage configuration must not be a string anymore. " +
                    "Please configure parameters as a list of strings. For example:\n" +
                    "appUrls: \n" + "  - url: 'https://my-url.com'\n" + "    parameters: ['--tag', 'scenario1']\n" +
                    "More details can be found in the documentation: https://sap.github.io/jenkins-library/steps/npmExecuteEndToEndTests/#parameters")
            }
        }
    }
}

private checkRenamedStep(Script script, String oldName, String newName) {
    if (loadEffectiveStepConfiguration(script: script, stepName: oldName)) {
        failWithConfigError("The configuration key ${oldName} in the steps configuration may not be used anymore. " +
            "Please use ${newName} instead.")
    }
}

private checkRenamedStage(Script script, String oldName, String newName) {
    if (loadEffectiveStageConfiguration(script: script, stageName: oldName)){
        failWithConfigError("The configuration key ${oldName} in the stages configuration may not be used anymore. " +
            "Please use ${newName} instead. " +
            "For more information please visit https://github.com/SAP/cloud-s4-sdk-pipeline/blob/master/configuration.md")
    }
}

private failWithConfigError(String errorMessage) {
    Analytics.instance.legacyConfig(true)
    error(errorMessage)
}

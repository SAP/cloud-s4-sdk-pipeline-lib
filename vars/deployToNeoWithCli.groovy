import com.sap.cloud.sdk.s4hana.pipeline.BashUtils
import com.sap.cloud.sdk.s4hana.pipeline.DeploymentType
import com.sap.cloud.sdk.s4hana.pipeline.NeoDeployCommandHelper
import com.sap.piper.ConfigurationHelper
import com.sap.piper.ConfigurationLoader
import com.sap.piper.ConfigurationMerger

def call(Map parameters = [:]) {

    handleStepErrors(stepName: 'deployToNeoWithCli', stepParameters: parameters) {

        final script = parameters.script

        final Map stepDefaults = ConfigurationLoader.defaultStepConfiguration(script, 'deployToNeoWithCli')

        final Map stepConfiguration = ConfigurationLoader.stepConfiguration(script, 'deployToNeoWithCli')

        Set parameterKeys = [
            'dockerImage',
            'deploymentType',
            'target',
            'source'
        ]

        Set stepConfigurationKeys = ['dockerImage']

        Map configuration = ConfigurationMerger.merge(parameters, parameterKeys, stepConfiguration, stepConfigurationKeys, stepDefaults)

        Map configurationHelper = new ConfigurationHelper(configuration)
            .withMandatoryProperty('dockerImage')
            .withMandatoryProperty('target')
            .withMandatoryProperty('source')
            .use()

        configuration.target.ev = transformEnvVarMapToStringList(configuration.target.ev)

        def dockerImage = configurationHelper.dockerImage
        def deploymentDescriptors = configurationHelper.target
        def source = configurationHelper.source

        Map deploymentDescriptor = new ConfigurationHelper(deploymentDescriptors).use()
        if (deploymentDescriptor.credentialsId) {
            NeoDeployCommandHelper commandHelper
            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: deploymentDescriptor.credentialsId, passwordVariable: 'NEO_PASSWORD', usernameVariable: 'NEO_USERNAME']]) {
                assertPasswordRules(NEO_PASSWORD)
                commandHelper = new NeoDeployCommandHelper(deploymentDescriptors, NEO_USERNAME, BashUtils.escape(NEO_PASSWORD), source)
                deploy(script, dockerImage, configuration.deploymentType, commandHelper)
            }
        } else {
            throw new Exception("ERROR - SPECIFY credentialsId")
        }

    }
}

private List transformEnvVarMapToStringList(def envVarMap) {
    List envVarsStringList = []
    def keys = envVarMap.keySet()
    for (int i = 0; i < keys.size(); i++) {
        envVarsStringList << "${BashUtils.escape(keys[i])}=${BashUtils.escape(envVarMap.get(keys[i]))}"
    }

    return envVarsStringList
}

private assertPasswordRules(String password){
    if(password.startsWith("@")){
        error("Your password for the deployment to SAP Cloud Platform contains characters which are not " +
            "supported by the neo tools. " +
            "For example it is not allowed that the password starts with @. " +
            "Please consult the documentation for the neo command line tool for more information: " +
            "https://help.sap.com/viewer/65de2977205c403bbc107264b8eccf4b/Cloud/en-US/8900b22376f84c609ee9baf5bf67130a.html")
    }
}

private deploy(script, dockerImage, DeploymentType deploymentType, NeoDeployCommandHelper commandHelper) {
    commandHelper.assertMandatoryParameters()
    dockerExecute(script: script, dockerImage: dockerImage) {
        lock("deployToNeoWithCli:${commandHelper.resourceLock()}") {

            if (deploymentType == DeploymentType.ROLLING_UPDATE) {
                if (!isAppRunning(commandHelper)) {
                    deploymentType = DeploymentType.STANDARD
                    echo "Rolling update not possible because application is not running. Falling back to standard deployment."
                }
            }

            echo "Link to the application dashboard: ${commandHelper.cloudCockpitLink()}"

            try {
                if (deploymentType == DeploymentType.ROLLING_UPDATE) {
                    sh commandHelper.rollingUpdateCommand()
                } else {
                    sh commandHelper.deployCommand()
                    sh commandHelper.restartCommand()
                }
            }
            catch (Exception ex) {
                echo "Error while deploying to SAP Cloud Platform. Here are the neo.sh logs:"
                sh "cat ${commandHelper.getNeoToolDirectory()}/log/*"
                throw ex
            }
        }
    }
}

private boolean isAppRunning(NeoDeployCommandHelper commandHelper) {
    def status = sh script: "${commandHelper.statusCommand()} || true", returnStdout: true
    return status.contains('Status: STARTED')
}

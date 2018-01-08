import com.sap.cloud.sdk.s4hana.pipeline.ConfigurationLoader
import com.sap.cloud.sdk.s4hana.pipeline.EndToEndTestType

def call(Map parameters = [:]) {
    def script = parameters.script
    runAsStage(stageName: 'productionDeployment', script: script) {
        unstashFiles script: script, stage: 'deploy'
        Map stageConfiguration = ConfigurationLoader.stageConfiguration(script, 'productionDeployment')

        if (fileExists('package.json')) {
            lock(script.pipelineEnvironment.configuration.productionDeploymentLock) {
                deployToCloudPlatform script: script, cfTargets: stageConfiguration.cfTargets, neoTargets: stageConfiguration.neoTargets, isProduction: true, stage: 'deploy'
                executeEndToEndTest script: script, appUrls: stageConfiguration.appUrls, endToEndTestType: EndToEndTestType.SMOKE_TEST, stage: 'deploy'
            }
        } else {
            deployToCloudPlatform script: script, cfTargets: stageConfiguration.cfTargets, neoTargets: stageConfiguration.neoTargets, isProduction: true, stage: 'deploy'
            echo "Smoke tests skipped, because package.json does not exist!"
        }
        stashFiles script: script, stage: 'deploy'
    }
}

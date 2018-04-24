def call(Map parameters) {
    def stageName = 'initS4sdkPipeline'
    def script = parameters.script

    loadPiper script: script

    runAsStage(stageName: stageName, script: script) {
        checkout scm
        initS4SdkPipelineLibrary script: script
        initStashConfiguration script: script

        def mavenLocalRepository = new File(script.s4SdkGlobals.m2Directory)
        def reportsDirectory = new File(script.s4SdkGlobals.reportsDirectory)

        mavenLocalRepository.mkdirs()
        reportsDirectory.mkdirs()
        if (!fileExists(mavenLocalRepository.absolutePath) || !fileExists(reportsDirectory.absolutePath)) {
            errorWhenCurrentBuildResultIsWorseOrEqualTo(
                script: script,
                errorCurrentBuildStatus: 'FAILURE',
                errorMessage: "Please check if the user can create report directory."
            )
        }

        Map generalConfiguration = script.commonPipelineEnvironment.configuration.general

        if(!generalConfiguration){
            generalConfiguration = [:]
            script.commonPipelineEnvironment.configuration.general = generalConfiguration
        }

        if (!generalConfiguration.projectName?.trim() && fileExists('pom.xml')) {
            pom = readMavenPom file: 'pom.xml'
            generalConfiguration.projectName = pom.artifactId
        }

        Map configWithDefault = loadEffectiveGeneralConfiguration script: script
        if (isProductiveBranch(script:script) && configWithDefault.automaticVersioning){
            artifactSetVersion script: script
        }
        generalConfiguration.gitCommitId = getGitCommitId()

        String prefix = generalConfiguration.projectName

        script.commonPipelineEnvironment.configuration.currentBuildResultLock = "${prefix}/currentBuildResult"
        script.commonPipelineEnvironment.configuration.performanceTestLock = "${prefix}/performanceTest"
        script.commonPipelineEnvironment.configuration.endToEndTestLock = "${prefix}/endToEndTest"
        script.commonPipelineEnvironment.configuration.productionDeploymentLock = "${prefix}/productionDeployment"
        script.commonPipelineEnvironment.configuration.stashFiles = "${prefix}/stashFiles"

        initStageSkipConfiguration script: script
    }
}



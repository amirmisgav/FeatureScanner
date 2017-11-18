package templates

public class MavenJobTemplates {

    static void setDisabled(def job, def args) {
        def flag = args[0]
        job.with {
            disabled(flag)
        }
    }

    static void setLabel(def job, def args) {
        def _label = args[0]
        job.with {
            label(_label)
        }
    }

    static void setJdk(def job, def args) {
        def _jdk = args[0]
        job.with {
            jdk(_jdk)
        }
    }

    static void setLogRotator(def job, def args) {
        def _daysToKeep = args[0]
        def _numToKeep = args[1]
        job.with {
            logRotator {
                daysToKeep(_daysToKeep)
                numToKeep(_numToKeep)
            }
        }
    }

    static void setDisableDownstreamTrigger(def job) {
        job.with {
            disableDownstreamTrigger()
        }
    }

    static void setBlockOnDownstreamProjects(def job) {
        job.with {
            blockOnDownstreamProjects()
        }
    }

    ///////////////////////////////////  PROPERTIES  ///////////////////////////////////////////////////////
    static void sidebarLinks(def job, def args) {
        def workspace = args[0]
        job.with {
            properties {
                sidebarLinks {
                    // use built-in image
                    link(workspace, 'WORKSPACE', '/static/dba52d9a/images/24x24/folder.png')
                }
            }
        }
    }

    static void addDescription(def job, def args) {
        def des = args[0]
        job.with {
            description(des)
        }
    }

    static void properties_feature_environment(def job, def args){
        def fullRepoName = args[0]
        def branchName = args[1]
        job.with {
            properties {
                envInjectJobProperty {
                    info {
                        propertiesFilePath('./workspace.properties')
                        propertiesContent('')
                        scriptFilePath('')
                        scriptContent("echo \'Checkout property file before SCM\' && curl -k -X GET -u GITHUB_USER:GITHUB_PASSWORD https://GITHUB_HOST/raw/${fullRepoName}/${branchName}/workspace.properties > ./workspace.properties && ls -la ./workspace.properties \n" +
                                " if grep -q  ArtifactsToDeploy= ./workspace.properties ; then \n" +
                                "         echo ArtifactsToDeploy exists ; \n " +
                                " else \n" +
                                " echo ArtifactsToDeploy= >>./workspace.properties ; \n" +
                                " fi \n" +
                                " cat ./workspace.properties")
                        groovyScriptContent('')
                        loadFilesFromMaster(false)
                    }
                    contributors {}
                    on(true)
                    keepJenkinsSystemVariables(true)
                    keepBuildVariables(true)
                    overrideBuildParameters(false)
                }
            }
        }
    }

    static void properties_buildDiscarder(def job, def args){
        def _numToKeepStr = args[0]
        job.with {
            properties {
                buildDiscarder {
                    strategy {
                        logRotator {
                            daysToKeepStr('-1')
                            numToKeepStr(_numToKeepStr)
                            artifactDaysToKeepStr('-1')
                            artifactNumToKeepStr('-1')
                        }
                    }
                }
            }
        }
    }

    ///////////////////////////////////  WRAPPERS  ///////////////////////////////////////////////////////
    static void wrappers_PRODUCT1dentialsBinding(def job, def args){
        def _usernameVariable = args[0]
        def _passwordVariable = args[1]
        def _PRODUCT1dentialsId = args[2]

        job.with {
            wrappers {
                PRODUCT1dentialsBinding {
                    usernamePasswordMultiBinding {
                        usernameVariable(_usernameVariable)
                        passwordVariable(_passwordVariable)
                        PRODUCT1dentialsId(_PRODUCT1dentialsId)
                    }
                }
            }
        }
    }

    static void wrappers_MaskPasswordsBuildWrapper(def job, def args){
        def _var = args[0]
        def _password = args[1]
        job.with {
            wrappers {
                maskPasswordsBuildWrapper{
                    varPasswordPairs{
                        varPasswordPair {
                            var(_var)
                            password(_password)
                        }
                    }
                }
            }
        }
    }

    static void configure_APPEND_MaskPasswordsBuildWrapper(def job, def args){
        def _path = args[0]
        def _password = args[1]
        job.with {
            configure {
                it / buildWrappers / 'com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper' / varPasswordPairs <<
                        varPasswordPair (var: _path, password: _password)
            }
        }
    }

    static void wrappers_NpmPackagesBuildWrapper(def job, def args){
        def _nodeJSInstallationName = args[0]
        job.with {
            wrappers {
                npmPackagesBuildWrapper {
                    nodeJSInstallationName(_nodeJSInstallationName)
                }
            }
        }
    }

    static void wrappers_timestamperBuildWrapper(def job){
        job.with {
            wrappers {
                timestamperBuildWrapper()
            }
        }
    }

    ///////////////////////////////////  TRIGGERS  //////////////////////////////////////////////////
    static void triggers_SCM(def job, def args) {
        def _cron = args[0]
        def _ignorePostCommitHooks = args[1]
        job.with {
            triggers {
                scm(_cron) {
                    ignorePostCommitHooks(_ignorePostCommitHooks)
                }
            }
        }
    }

    ///////////////////////////////////  SCM  ///////////////////////////////////////////////////////
    static void multiscm_gitSCM(def job, def args){
        def _url = args[0]
        def branchName = args[1]
        def _gitTool = args[2]
        job.with {
            multiscm{
                gitSCM{
                    userRemoteConfigs {
                        userRemoteConfig {
                            // Specify the URL of this remote repository.
                            url(_url)
                            // ID of the repository, such as origin, to uniquely identify this repository among other remote repositories.
                            name('')
                            // A refspec controls the remote refs to be retrieved and how they map to local refs.
                            refspec('')
                            PRODUCT1dentialsId('')
                        }
                    }
                    branches {
                        branchSpec {
                            // Specify the branches if you'd like to track a specific branch in a repository.
                            name(branchName)
                        }
                    }
                    gitTool(_gitTool)
                    doGenerateSubmoduleConfigurations(false)
                    extensions{
                        localBranch{
                            localBranch(branchName)
                        }
                        cleanBeforeCheckout()
                        wipeWorkspace()
                    }
                    browser{}
                }
            }
        }
    }

    ///////////////////////////////////  PRE STEPS  ///////////////////////////////////////////////////////
    static void preSteps_HandleSettingXML(def job, def args){
        def branchName = args[0]
        job.with {
            preBuildSteps {
                shell("env \n" +
                        "echo 'Checkout settings.xml ' \n" +
                        "   curl -k -X GET -u GITHUB_USER:GITHUB_PASSWORD https://GITHUB_HOST/raw/RE/PipeLines/master/config/settings.xml > \${WORKSPACE}/settings.xml \n" +
                        "   ls -la \${WORKSPACE}/settings.xml \n" +
                        "   sed -i 's/VIRTUAL-REPO/${branchName}-virt/g' \${WORKSPACE}/settings.xml \n" +
                        "   cat \${WORKSPACE}/settings.xml \n" +
                        "   curl -v --user \${ArtifactoryARTIFACTORY_USERUser}:\${ArtifactoryARTIFACTORY_USERPassword}  -T \${WORKSPACE}/settings.xml -X PUT \"http://ARTIFACTORY_HOST:8081/artifactory/${branchName}-local/settings.xml\" \n" +
                        "   git checkout ${branchName} \n" +
                        "   git add settings.xml \n" +
                        "   git commit -m \"add settings.xml\" || true \n" +
                        "   git push")
            }
        }
    }

    static void setArchivingDisabled(def job, def args){
        def flag = args[0]
        job.with {
            archivingDisabled(flag)
        }
    }

    static void preSteps_Shell(def job, def args){
        def shell_content = args[0]
        job.with {
            preBuildSteps {
                shell(shell_content)
            }
        }
    }

    static void preSteps_proxyBuilder(def job, def args) {
        def _projectName = args[0]
        job.with {
            preBuildSteps {
                proxyBuilder {
                    projectName(_projectName)
                }
            }
        }
    }

    ///////////////////////////////////   BUILD  ///////////////////////////////////////////////////////
    static void configure_Settings(def job, def args){
        def _path = args[0]
        job.with {
            configure {
                it / settings(class: 'jenkins.mvn.FilePathSettingsProvider') <<
                        path(_path)
            }
        }
    }

    static void configure_GlobalSettings(def job, def args){
        def _path = args[0]
        job.with {
            configure{
                it / globalSettings (class: 'jenkins.mvn.FilePathGlobalSettingsProvider') <<
                        path(_path)
            }
        }
    }

    static void setMvnSettingsProvider(def job, def args){
        def _settingsConfigId = args[0]
        job.with {
            configure {
                it / settings(class: 'org.jenkinsci.plugins.configfiles.maven.job.MvnSettingsProvider') <<
                        settingsConfigId(_settingsConfigId)
            }

    //            settings {
    //                mvnSettingsProvider {
    //                    settingsConfigId(_settingsConfigId)
    //                }
    //            }
        }
    }

    static void setMvnGlobalSettingsProvider(def job, def args){
        def _settingsConfigId = args[0]
        job.with {
            configure {
                it / globalSettings(class: 'org.jenkinsci.plugins.configfiles.maven.job.MvnGlobalSettingsProvider') <<
                        settingsConfigId(_settingsConfigId)
            }

    //            globalSettings {
    //                mvnGlobalSettingsProvider {
    //                    settingsConfigId(_settingsConfigId)
    //                }
    //            }
        }
    }

    static void setSiteArchivingDisabled(def job, def args){
        def flag = args[0]
        job.with {
            siteArchivingDisabled(flag)
        }
    }

    static void setFingerprintingDisabled(def job, def args){
        def flag = args[0]
        job.with {
            fingerprintingDisabled(flag)
        }
    }

    static void setGoals(def job, def args){
        def _goal = args[0]
        job.with {
            goals(_goal)
        }
    }

    static void setMavenOpts(def job, def args){
        def _mavenOpts = args[0]
        job.with {
            mavenOpts(_mavenOpts)
        }
    }

    static void setMavenInstallation(def job, def args){
        def mavenInst = args[0]
        job.with {
            mavenInstallation(mavenInst)
        }
    }

    static void setLocalRepository(def job, def args){
        def localRepo = args[0]
        job.with {
            localRepository(localRepo)
        }
    }

    static void setrunHeadless(def job, def args){
        def flag = args[0]
        job.with {
            runHeadless(flag)
        }
    }

    ///////////////////////////////////  POST STEPS  ///////////////////////////////////////////////////////

    static void postSteps_ArtifactARTIFACTORY_USER(def job, def args){
        def _includes = args[0]
        def _remoteFileLocation = args[1]
        job.with {
            postBuildSteps {
                //	shell("echo 'run after Maven'")
                artifactARTIFACTORY_USER {
                    includes(_includes)
                    //	baseDir('target')
                    remoteFileLocation(_remoteFileLocation)
                    //	deleteRemoteArtifacts()
                }

            }
        }
    }

    static void postSteps_Shell_BY_STATUS(def job, def args){
        def status = args[0]
        def shell_content = args[1]
        job.with {
            postBuildSteps(status) {
                shell(shell_content)
            }
        }
    }


    //////////////////////////////  POST BUILD ACTIONS  ///////////////////////////////////////////////////////


    ///////////////////////////////////  PUBLISHERS  ///////////////////////////////////////////////////////

    static void publishers_ArtifactARTIFACTORY_USERPublisher(def job, def args){
        def _includes = args[0]
        def _remote = args[1]
        def _deployEvenBuildFail = args[2]
        job.with {
            publishers {
                artifactARTIFACTORY_USERPublisher {
                    deployedArtifact {
                        artifactARTIFACTORY_USEREntry {
                            includes(_includes)
                            basedir('')
                            excludes('')
                            remote(_remote)
                            flatten(false)
                            deleteRemote(false)
                            deleteRemoteArtifacts(false)
                            deleteRemoteArtifactsByScript {
                                groovyExpression('')
                            }
                            failNoFilesDeploy(false)
                        }
                    }
                    deployEvenBuildFail(_deployEvenBuildFail)
                }
            }
        }
    }

    static void publishers_DiscardBuildPublisher(def job, def args){
        job.with {
            publishers {
                discardBuildPublisher {
                    daysToKeep('-1')
                    intervalDaysToKeep('-1')
                    numToKeep('5')
                    intervalNumToKeep('-1')
                    discardSuccess(false)
                    discardUnstable(false)
                    discardFailure(true)
                    discardNotBuilt(false)
                    discardAborted(false)
                    minLogFileSize('-1')
                    maxLogFileSize('-1')
                    regexp('')
                }
            }
        }
    }

    static void publishers_ExtendedEmailPublisher(def job, def args){
        def _recipient_list = args[0]
        job.with {
            publishers {
                extendedEmailPublisher {
                    project_recipient_list(_recipient_list)
                    project_triggers {
                        unstableTrigger {
                            recipientProviders {
                                // Sends email to the list of users who committed a change since the last non-broken build till now.
                                //culpritsRecipientProvider('')
                                // Sends email to all the people who caused a change in the change set.
                                //developersRecipientProvider('')
                                // Sends email to the list of users suspected of causing a unit test to begin failing.
                                //failingTestSuspectsRecipientProvider('')
                                // Sends email to the list of users suspected of causing the build to begin failing.
                                //firstFailingBuildSuspectsRecipientProvider('')
                                // Sends email to the list of recipients defined in the "Project Recipient List."
                                //listRecipientProvider('')
                                // Sends email to the user who initiated the build.
                                //requesterRecipientProvider('')
                                // Sends email to the list of users who committed changes in upstream builds that triggered this build.
                                //upstreamComitterRecipientProvider('')
                            }
                            recipientList(_recipient_list)
                            replyTo('')
                            subject('')
                            body('')
                            attachmentsPattern('')
                            attachBuildLog(0)
                            contentType('project')
                            contentType('default')
                            //recipientProviders('')
                            project_content_type('')
                            project_default_subject('')
                            project_attachments('')
                            project_presend_script('')
                            project_attach_buildlog(0)
                            project_replyto('')
                            project_save_output(false)
                            matrixTriggerMode('ONLY_PARENT')
                            project_disabled(false)
                            recipientList('')
                            project_default_content('')

                        }
                        failureTrigger {
                            recipientProviders {
                                // Sends email to the list of users who committed a change since the last non-broken build till now.
                                //culpritsRecipientProvider('')
                                // Sends email to all the people who caused a change in the change set.
                                //developersRecipientProvider('')
                                // Sends email to the list of users suspected of causing a unit test to begin failing.
                                //failingTestSuspectsRecipientProvider('')
                                // Sends email to the list of users suspected of causing the build to begin failing.
                                //firstFailingBuildSuspectsRecipientProvider('')
                                // Sends email to the list of recipients defined in the "Project Recipient List."
                                //listRecipientProvider('')
                                // Sends email to the user who initiated the build.
                                //requesterRecipientProvider('')
                                // Sends email to the list of users who committed changes in upstream builds that triggered this build.
                                //upstreamComitterRecipientProvider('')
                            }
                            recipientList(_recipient_list)
                            replyTo('')
                            subject('')
                            body('')
                            attachmentsPattern('')
                            attachBuildLog(0)
                            contentType('project')
                            contentType('default')
                            //recipientProviders('')
                            project_content_type('')
                            project_default_subject('')
                            project_attachments('')
                            project_presend_script('')
                            project_attach_buildlog(0)
                            project_replyto('')
                            project_save_output(false)
                            matrixTriggerMode('ONLY_PARENT')
                            project_disabled(false)
                            recipientList('')
                            project_default_content('')

                        }

                    }

                }
            }
        }
    }

    static void publishers_ArtifactoryRedeployPublisher(def job, def args){
        def _deployReleaseRepository = args[0]
        def _deploySnapshotRepository = args[1]
        def _evenIfUnstable = args[2]
        job.with {
            publishers {
                artifactoryRedeployPublisher{
                    runChecks(false)
                    violationRecipients('')
                    includePublishArtifacts(false)
                    passIdentifiedDownstream(false)
                    scopes('')
                    disableLicenseAutoDiscovery(false)
                    discardOldBuilds(false)
                    discardBuildArtifacts(true)
                    matrixParams('')
                    enableIssueTrackerIntegration(false)
                    allowPromotionOfNonStagedBuilds(true)
                    allowBintrayPushOfNonStageBuilds(false)
                    filterExcludedArtifactsFromBuild(true)
                    recordAllDependencies(false)
                    defaultPromotionTargetRepository('')
                    deployBuildInfo(true)
                    aggregationBuildStatus('Released')
                    aggregateBuildIssues(false)
                    blackDuckRunChecks(false)
                    blackDuckAppName('')
                    blackDuckAppVersion('')
                    blackDuckReportRecipients('')
                    blackDuckScopes('')
                    blackDuckIncludePublishedArtifacts(false)
                    autocreateMissingComponentRequests(true)
                    autoDiscardStaleComponentRequests(true)
                    evenIfUnstable(true)
                    details{
                        resolveReleaseRepository(null)
                        resolveSnapshotRepository(null)
                        userPluginKey(null)
                        userPluginParams(null)
                        artifactoryName('repo1')
                        artifactoryUrl('http://ARTIFACTORY_HOST:8081/artifactory')
                        deployReleaseRepository{
                            keyFromText(null)
                            keyFromSelect(_deployReleaseRepository)
                            dynamicMode(false)
                        }
                        deploySnapshotRepository{
                            keyFromText('null')
                            keyFromSelect(_deploySnapshotRepository)
                            dynamicMode(false)
                        }
                    }

                    deployArtifacts(true)
                    artifactDeploymentPatterns{
                        includePatterns('')
                        excludePatterns('')
                    }
                    ARTIFACTORY_USERPRODUCT1dentialsConfig{

                        username('')
                        password('')
                        PRODUCT1dentialsId('')
                        overridingPRODUCT1dentials(false)

                    }
                    includeEnvVars(true)
                    envVarsPatterns{
                        includePatterns('')
                        excludePatterns('*password*,*sePRODUCT1t*,*Password*')
                    }
                }
            }
        }
    }

    static void publishers_DescriptionSetterPublisher(def job, def args){
        def _description = args[0]
        job.with {
            publishers {
                descriptionSetterPublisher {
                    regexp('')
                    regexpForFailed('')
                    description(_description)
                    descriptionForFailed('')
                    setForMatrix(false)
                }
            }
        }
    }


}

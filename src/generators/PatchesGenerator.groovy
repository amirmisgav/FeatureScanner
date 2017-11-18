package generators


import groovy.transform.Field
import hudson.*
import jenkins.*
import templates.*


//Get all build environment variables
def configuration = new HashMap()
def binding = getBinding()
configuration.putAll(binding.getVariables())

def productFile_maven = configuration['WORKSPACE'] + '/config/patches_maven.properties'
def productFile_freeStyle = configuration['WORKSPACE'] + '/config/patches_freeStyle.properties'
def environment_requested_product = configuration['PRODUCT']

@Field hudson = hudson.model.Hudson.instance
//@Field MyUtilities = templates.MyUtilities
@Field hashProductBranches=[:]
@Field BRANCH="master"
@Field TEMPLATE_PIPELINE_NAME = "PRODUCT.pipeline.TEMPLATE" // Pipeline multi module job template
@Field TEMPLATE_AGGREGATOR_NAME = "PRODUCT.TEMPLATE" // Pipeline multi module job template

println "                    \n\n============================        MAVEN TYPE BUILD       ============================\n"
generateForType(productFile_maven, environment_requested_product)

println "                   \n\n============================        FREE STYLE TYPE BUILD       ============================\n"
generateForType(productFile_freeStyle, environment_requested_product)

println "                   \n\n============================        END       ============================\n"

//   **************************************************************************************
//   ***********************************   GROOVY METHODS     *****************************
//   **************************************************************************************

def generateForType(def productFile, def env_requested_product){
    def productList = readProductFile(productFile)
    productList.eachWithIndex { it, index ->
        it.each { k, v ->
            def PRODUCT = "${k}"
            if(env_requested_product != null && PRODUCT.equalsIgnoreCase(env_requested_product)){
                println " \n\n ***************  PRODUCT : " + PRODUCT + "  ***************\n"
                def reponame = v.tokenize(',')
                reponame.each {
                    def repoName = it
                    println " \n------- REPOSITORY  : " + repoName + "  -------\n"
                    println "\t$k = $v"
                    def hashbranches = createHashBranchesForRepo(repoName)
                    hashProductBranches.putAll(hashbranches)
                    createJobForRepo_Hash(repoName, PRODUCT, hashbranches)
                    createPipelineForRepo_Hash(repoName, PRODUCT, hashbranches)
                }
                //Todo : add to aggregator release active choice paramters once the dsl enables it
//                createAggregator(hashProductBranches, PRODUCT)
                hashProductBranches.clear()
            }
        }
    }
}

def readProductFile(def propertyFile){
    //This will read the data in blocks (with blank lines separating the blocks)
    def productList = []
    File productFile = new File( propertyFile )
    if( !productFile.exists() ) {
        println "File does not exist"
    } else {
        def productRepos = [:]
        // Step through each line in the file
        productFile.eachLine { line ->
            // If the line isn't blank
            if( line.trim() ) {
                // Split into a key and value
                def (key,value) = line.split( ' ' ).collect { it.trim() }
                // and store them in the productRepos Map
                productRepos."$key" = value
            }
        }
        // when we've finished the file, store any remaining data
        if( productRepos ) {
            productList << productRepos
        }
    }
    return productList
}

def createHashBranchesForRepo(def repo) {
    hashbranches=[:]
    def branches
    end = false
    page = 1
    while(!end) {
        def authString = "GITHUB_USER:GITHUB_PASSWORD".getBytes().encodeBase64().toString();
        URLConnection connBranches = new URL("https://GITHUB_HOST/api/v3/repos/${repo}/branches?per_page=100&page=$page").openConnection();
        connBranches.setRequestProperty("Authorization", "Basic ${authString}");
        branches = new groovy.json.JsonSlurper().parse(new BufferedReader(new InputStreamReader(connBranches.getInputStream())));
        if ( branches.results.size() == 0 ) {
            end = true
            break
        }
        def FI = jm.getParameters() ["FILTER"] ?: "ftr-"
        FILTER= ~/$FI.*/
        branches.each { branch ->
            if (branch.name.matches(FILTER)) {
                String branchName = branch.name.replaceAll('/', '-')
                hashbranches[branchName] = 1
            }
        }
        page++
    }
    return hashbranches
}

def createJobForRepo_Hash(def repo, def PRODUCT,def hashbranches){
    def repoFilter = ~/.*?\//
    String safeRepoName = repo.replaceAll(repoFilter,'') //  configuration file fore job per repository
    def projectGitSshUrlToRepo = "git@GITHUB_HOST:${repo}.git"
    println "\n ----- create JOBS FOR PRODUCT " + PRODUCT + " REPOSITORY " + repo + "------\n"
    hashbranches.keySet().each { branch ->
        String branchName = branch.replaceAll('/', '-')
        def ciJobName = "${safeRepoName}_${branchName}"
        println "-> create Maven Job: ${ciJobName}"
        List args = [ciJobName.toString(), projectGitSshUrlToRepo.toString(), branchName.toString(),PRODUCT.toString(),repo.toString()]
        def create_MAVEN_JOB_METHOD_NAME="createMavenJob_DSL_"+PRODUCT+"_PATCH_SET"
        println create_MAVEN_JOB_METHOD_NAME
        "$create_MAVEN_JOB_METHOD_NAME"(args)
    }
}

def createPipelineForRepo_Hash(def repo, def PRODUCT,def hashbranches){
    println "\n ----- create PIPELINES FOR PRODUCT " + PRODUCT + " REPOSITORY " + repo + "------\n"
    def repoFilter = ~/.*?\//
    String safeRepoName = repo.replaceAll(repoFilter,'')
    hashbranches.keySet().each { branch ->
        println "--> Branch :  " + branch + " is found and handled now"
        def targetJob= TEMPLATE_PIPELINE_NAME.replaceAll('TEMPLATE',branch).replaceAll('PRODUCT',safeRepoName)
        def folderName = PRODUCT +"/Patches"  + "/" + branch
        def pipelineFullPath = folderName + "/" + targetJob
        def script_Path = "src/product/PRODUCT2/Jenkinsfile       List args = [pipelineFullPath.toString(),BRANCH.toString(), script_Path]
        def PIPELINE_METHOD_NAME="createPipeline_DSL_"+PRODUCT
        "$PIPELINE_METHOD_NAME"(args)
    }
}

def createPipeline(def hashbranches, def PRODUCT){
    println "\n ----- create PIPELINES FOR PRODUCT " + PRODUCT + "  ------\n"
    hashbranches.each {
        def branch=it.key
        println "--> Branch :  " + branch + " is found and handled now"
        def targetJob= TEMPLATE_PIPELINE_NAME.replaceAll('TEMPLATE',branch).replaceAll('PRODUCT',PRODUCT)
        def folderName = PRODUCT +"/Patches"  + "/" + branch
        def pipelineFullPath = folderName + "/" + targetJob
        List args = [pipelineFullPath.toString(),BRANCH.toString()]
        def PIPELINE_METHOD_NAME="createPipeline_DSL_"+PRODUCT
        "$PIPELINE_METHOD_NAME"(args)
    }
}

def createAggregator(def hashbranches, def PRODUCT){
    println "\n ----- create AGGREGATOR FOR PRODUCT " + PRODUCT + "  ------\n"
    hashbranches.each {
        def branch=it.key
        String branchName = branch.replaceAll('/', '-')
        println "---> Branch :  " + branch + " is found and handled now"
        def targetJob= TEMPLATE_AGGREGATOR_NAME.replaceAll('TEMPLATE',branch).replaceAll('PRODUCT',PRODUCT)
//        def folderName = PRODUCT +"/Patches"  + "/" + branch
//        def aggregatorFullPath = folderName + "/" + targetJob
        List args = [targetJob.toString(), branchName.toString(),PRODUCT.toString()]
        def AGGREGATOR_METHOD_NAME="createAggregator_DSL_"+PRODUCT
        "$AGGREGATOR_METHOD_NAME"(args)
    }
}

//   **************************************************************************************
//   ************************************ DSL METHODS   *************************************
//   **************************************************************************************

//                  ****************************************************
//                  *****************     PIPELINES     ****************
//                  ****************************************************
//                          *********     PRODUCT1           *********
def createPipeline_DSL_PRODUCT1(List args){
    println " !!!!!!!!!!!!!!     PRODUCT1_PI  NO PIPELINE       !!!!!!!!!!!!!!!!!!!!"
}

//                          *********     PRODUCT2            *********
def createPipeline_DSL_PRODUCT2(List args){
    String script_Path = "src/product/PRODUCT2/Jenkinsfile args.add(script_Path)
    createPipeline_DSL(args)
}

//                          *********     PRODUCT3            *********
def createPipeline_DSL_PRODUCT3(List args){
//    def script_Path = "src/product/PRODUCT2/Jenkinsfile   args.add(script_Path)
    //createPipeline_DSL(args)
}

//                          *********     PRODUCT4            *********
def createPipeline_DSL_PRODUCT4(List args){
//    def script_Path = "src/product/PRODUCT2/Jenkinsfile   args.add(script_Path)
    //createPipeline_DSL(args)
}

//                          *********     COMMON            *********
def createPipeline_DSL(List args){
    def pipelineFullPath = args[0]
    def defaultBranch = args[1]
    def script_Path = args[2]
    defaultBranch = jm.getParameters() ["BRANCH"] ?: defaultBranch

    def repo = "RE/PipeLines"
    def projectGitSshUrlToRepo = "git@GITHUB_HOST:${repo}.git"
//    def script_Path = "src/product/PRODUCT2/Jenkinsfile   println " --  create/VALIDATE PIPELINE : " + pipelineFullPath + "--"
    pipelineJob("${pipelineFullPath}") {
        properties{
            disableConcurrentBuilds()
            pipelineTriggers{
                triggers{
                    scm{
                        scmpoll_spec('*/5 * * * *')
                        ignorePostCommitHooks(false)
                    }
                }
            }
        }
        multiscm{
            gitSCM{
                userRemoteConfigs {
                    userRemoteConfig {
                        // Specify the URL of this remote repository.
                        url(projectGitSshUrlToRepo.toString())

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
                        name(defaultBranch)
                    }
                }
                gitTool('Git_1.7_x64')
                doGenerateSubmoduleConfigurations(false)
                browser{}
            }

        }
        definition {
            cpsScm  {
                scm{
                    gitSCM{
                        branches{
                            branchSpec {
                                name(BRANCH)
                            }
                        }
                        browser {}
                        doGenerateSubmoduleConfigurations(false)
                        extensions {
                            pathRestriction {
                                includedRegions('')
                                excludedRegions('.*')

                            }
                            wipeWorkspace()

                        }
                        gitTool("Git_1.7_x64")
                        userRemoteConfigs {
                            userRemoteConfig {
                                url(projectGitSshUrlToRepo.toString())
                                PRODUCT1dentialsId('')
                                name('')
                                refspec('')
                            }
                        }

                    }
                    scriptPath(script_Path)

                }
            }
        }
    }
}


//                  ****************************************************
//                  *****************     AGGREGATORS     ****************
//                  ****************************************************

def createAggregator_DSL_PRODUCT2(List args){

    def ciJobName = args[0]
    def branchName = args[1]
    def PRODUCT = args[2]
    def projectGitSshUrlToRepo = 'git@GITHUB_HOST:ORG/COMPONENT11.git'

    println( "ciJobName  $ciJobName")
    println( "branchName  $branchName")


    String productFolder = PRODUCT + "/Patches"
    String patchesFolder = PRODUCT + "/Patches/"  + branchName

    FolderDSL.createFolder_DSL(this, productFolder)
    FolderDSL.createFolder_DSL(this, patchesFolder)


    def maven_repo_local="/workspace/workspace/." + branchName +"_repo"
    // propertyFile is injected into Job Environments
    def branchNameLocal = branchName + "-local"
    def link2Workspace = "http://WEB_HOST/new_workspace/" + PRODUCT + "/Patches/" + branchName + "/" + ciJobName
    def _recipient_list = '\$DEFAULT_RECIPIENTS'
    def mavenJob_dsl_PRODUCT2 = mavenJob("${patchesFolder}/${ciJobName}") {

        properties {
            promotions {
                promotion {
                    name('Release')
                    icon("star-gold")
                    conditions {
                        manual('')
                    }
                    actions {
                        shell("curl -k -X GET -u GITHUB_USER:GITHUB_PASSWORD https://GITHUB_HOST/raw/RE/JenkinsTools/master/src/groovy/CheckArtifactoryTasks.groovy > ./CheckArtifactoryTasks.groovy && ls -la ./CheckArtifactoryTasks.groovy")
                        shell("curl -k -X GET -u GITHUB_USER:GITHUB_PASSWORD https://GITHUB_HOST/raw/RE/JenkinsTools/master/src/groovy/replicaCRArtifacts.groovy > ./replicaCRArtifacts.groovy && ls -la ./replicaCRArtifacts.groovy")
                        shell("curl -k -X GET -u GITHUB_USER:GITHUB_PASSWORD https://GITHUB_HOST/raw/RE/JenkinsTools/master/src/groovy/replicaCRArtifactsUK.groovy > ./replicaCRArtifactsUK.groovy && ls -la ./replicaCRArtifactsUK.groovy")
                        shell("curl -k -X GET -u GITHUB_USER:GITHUB_PASSWORD https://GITHUB_HOST/raw/RE/JenkinsTools/master/src/groovy/SetReplicaScheduler.groovy > ./SetReplicaScheduler.groovy && ls -la ./SetReplicaScheduler.groovy")

                        systemGroovyScriptFile("CheckArtifactoryTasks.groovy") {
//                            binding(String name, String value)
                            classpath("\${WORKSPACE}")
                        }

                        systemGroovyScriptFile("replicaCRArtifacts.groovy") {
//                            binding(String name, String value)
                            classpath("\${WORKSPACE}")
                        }

                        systemGroovyScriptFile("replicaCRArtifactsUK.groovy") {
//                            binding(String name, String value)
                            classpath("\${WORKSPACE}")
                        }

                        systemGroovyScriptFile("SetReplicaScheduler.groovy") {
//                            binding(String name, String value)
                            classpath("\${WORKSPACE}")
                        }


//
                    }
                    publishers {
                        keepBuildForeverAction()
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
        }
    }

    wrappers {
        mavenReleaseWrapper {

        }
    }

    MavenJobTemplates.setDisabled(mavenJob_dsl_PRODUCT2,[false])
    MavenJobTemplates.setLabel(mavenJob_dsl_PRODUCT2,["LABEL_${PRODUCT}"])
    MavenJobTemplates.setJdk(mavenJob_dsl_PRODUCT2,['jdk1.8_045_linux'])
    MavenJobTemplates.addDescription(mavenJob_dsl_PRODUCT2,[branchName])
    MavenJobTemplates.sidebarLinks(mavenJob_dsl_PRODUCT2,[link2Workspace])
    MavenJobTemplates.properties_buildDiscarder(mavenJob_dsl_PRODUCT2,['10'])
    MavenJobTemplates.multiscm_gitSCM(mavenJob_dsl_PRODUCT2, [projectGitSshUrlToRepo.toString(), branchName, 'Git_1.7_x64'])
    MavenJobTemplates.wrappers_timestamperBuildWrapper(mavenJob_dsl_PRODUCT2)
    MavenJobTemplates.triggers_SCM(mavenJob_dsl_PRODUCT2,['*/10 * * * *',false])

    // Build

    MavenJobTemplates.setArchivingDisabled(mavenJob_dsl_PRODUCT2,[true])
//    MavenJobTemplates.setSiteArchivingDisabled(mavenJob_dsl_PRODUCT2,[true])
    MavenJobTemplates.setFingerprintingDisabled(mavenJob_dsl_PRODUCT2,[true])
    MavenJobTemplates.setDisableDownstreamTrigger(mavenJob_dsl_PRODUCT2)
    MavenJobTemplates.setBlockOnDownstreamProjects(mavenJob_dsl_PRODUCT2)
    MavenJobTemplates.setGoals(mavenJob_dsl_PRODUCT2,['clean install  -P master -Denvironment=master'])
    MavenJobTemplates.setMavenOpts(mavenJob_dsl_PRODUCT2,['-XX:MaxPermSize=512m -XX:PermSize=512m -Xms512m -Xmx2560m -XX:-UseGCOverheadLimit -client -Dmaven.test.failure.ignore=false'])
    MavenJobTemplates.setMavenInstallation(mavenJob_dsl_PRODUCT2,['mvn_3.3.9'])
    MavenJobTemplates.setLocalRepository(mavenJob_dsl_PRODUCT2,[LocalRepositoryLocation.LOCAL_TO_WORKSPACE])
    MavenJobTemplates.setMvnSettingsProvider(mavenJob_dsl_PRODUCT2,['cfcb170c-3868-45d7-a30a-99e55747e56b'])
    MavenJobTemplates.setMvnGlobalSettingsProvider(mavenJob_dsl_PRODUCT2,['03e9a980-bf05-44af-9491-3563ca389ef2'])

    // POST STEPS
//    MavenJobTemplates.postSteps_ArtifactARTIFACTORY_USER(mavenJob_dsl_PRODUCT2,['**/target/*.*ar','/fs/artifacts/\${JOB_NAME}/\${BUILD_NUMBER}/\${GIT_COMMIT}'])

    // POST BUILD
    // Publishers
    MavenJobTemplates.publishers_ArtifactoryRedeployPublisher(mavenJob_dsl_PRODUCT2,[branchNameLocal,'libs-snapshot-local',false])
    MavenJobTemplates.publishers_DescriptionSetterPublisher(mavenJob_dsl_PRODUCT2,['CL-\${GIT_COMMIT}'])
    //MavenJobTemplates.publishers_DiscardBuildPublisher(mavenJob_dsl_PRODUCT2,[])
    MavenJobTemplates.publishers_ArtifactARTIFACTORY_USERPublisher(mavenJob_dsl_PRODUCT2,['**/target/*.*ar','/fs/artifacts/\${JOB_NAME}/\${BUILD_NUMBER}/\${GIT_COMMIT}',false])
    MavenJobTemplates.publishers_ExtendedEmailPublisher(mavenJob_dsl_PRODUCT2,['\$DEFAULT_RECIPIENTS,'])

}



//                 *******************************************************
//                 ******************     MAVEN JOBS     *****************
//                 *******************************************************
//                          *********     PRODUCT1           *********
def createMavenJob_DSL_PRODUCT1(List args){

    createmavenJob_dsl(args)
}

def createmavenJob_dsl(List args){

    def ciJobName = args[0]
    ciJobName = "pi_" + ciJobName
    def projectGitSshUrlToRepo = args[1]
    def branchName = args[2]
    def PRODUCT = args[3]
    def fullRepoName = args[4]
    def git_repo_name = fullRepoName.substring(fullRepoName.lastIndexOf("/")+1)

    String productFolder = PRODUCT + "/Patches"
    String patchesFolder = PRODUCT + "/Patches/"  + branchName

    FolderDSL.createFolder_DSL(this, productFolder)
    FolderDSL.createFolder_DSL(this, patchesFolder)



    def maven_repo_local="/workspace/workspace/." + branchName +"_repo"
    // propertyFile is injected into Job Environments
    def branchNameLocal = branchName + "-local"
    def link2Workspace = "http://WEB_HOST/new_workspace/" + PRODUCT + "/Patches/" + branchName + "/" + ciJobName

    def mavenJob_dsl_PRODUCT1 =  mavenJob("${patchesFolder}/${ciJobName}") {
        disabled(false)
        //label("LABEL_PRODUCT2")
        label("LABEL_${PRODUCT}_${git_repo_name}")
        logRotator {
            daysToKeep(-1)
            numToKeep(20)
        }

        properties {
            parameters {
                parameterDefinitions {
                    wHideParameterDefinition {
                        name('GIT_REPO')
                        defaultValue("${git_repo_name}")
                        description('')
                    }
                    wHideParameterDefinition {
                        name('GIT_BRANCH')
                        defaultValue("${branchName}")
                        description('')
                    }
                    booleanParam {
                        name('IgnoreTestFailure')
                        defaultValue(false)
                        description('Please select if you want the job to ignore tests errors')
                    }
                    choiceParam {
                        name('Debug')
                        choices('-V\n-X')
                        description('Please select -X if you want the build to run in debug mode')
                    }
                    booleanParam {
                        name('SkipAllTestExec')
                        defaultValue(false)
                        description('Please select if you want to disable test execution')
                    }
                    cascadeChoiceParameter {
                        name('Profiles')
                        description('')
                        randomName('')
                        script {
                            groovyScript {
                                script("def pomFileList = [] as List<String> \n" +
                                        "def profileList = [] as List<String> \n" +
                                        "Process p1 = \"git --git-dir=/fs/ENGINEERING_DIR/pi_git_repos/\${GIT_REPO}/.git --work-tree=/fs/ENGINEERING_DIR/pi_git_repos/\${GIT_REPO}/ pull\".execute() \n" +
                                        "Process p2 = \"git --git-dir=/fs/ENGINEERING_DIR/pi_git_repos/\${GIT_REPO}/.git --work-tree=/fs/ENGINEERING_DIR/pi_git_repos/\${GIT_REPO}/ ls-tree -r --name-only remotes/origin/\${GIT_BRANCH}\".execute() \n" +
                                        "Process p3 = 'grep pom.xml'.execute() \n" +
                                        "Process all = p2 | p3 \n" +
                                        "pomFileList = all.getText().split() \n" +
                                        "for (pomFile in pomFileList) { \n" +
                                        "   Process p4 = \"git --git-dir=/fs/ENGINEERING_DIR/pi_git_repos/\${GIT_REPO}/.git --work-tree=/fs/ENGINEERING_DIR/pi_git_repos/\${GIT_REPO}/ show remotes/origin/\${GIT_BRANCH}:\${pomFile}\".execute() \n" +
                                        "   pomXml=new XmlSlurper().parseText(p4.getText()) \n" +
                                        "   pomXml.profiles.children().each \n" +
                                        "   { " +
                                        "       profileList.add(it.id.text()) \n" +
                                        "   } \n" +
                                        "} \n" +
                                        "return profileList.sort()")
                                fallbackScript ("return['']")
                            }
                        }
                        choiceType('PT_MULTI_SELECT')
                        referencedParameters('GIT_REPO,GIT_BRANCH')
                        filterable(false)
                    }
                    cascadeChoiceParameter {
                        name('Modules')
                        description('')
                        randomName('')
                        script {
                            groovyScript {
                                script("def pomFileList = [] as List<String>\n" +
                                        "def moduleList= [] as List<String>\n" +
                                        "\n" +
                                        "Process p2 = \"git --git-dir=/fs/ENGINEERING_DIR/pi_git_repos/\${GIT_REPO}/.git --work-tree=/fs/ENGINEERING_DIR/pi_git_repos/\${GIT_REPO}/ ls-tree -r --name-only remotes/origin/\${GIT_BRANCH}\".execute()\n" +
                                        "Process p3 = 'grep pom.xml'.execute() \n" +
                                        "\n" +
                                        "Process all = p2 | p3\n" +
                                        "\n" +
                                        "pomFileList = all.getText().split() \n" +
                                        "\n" +
                                        "for (pomFile in pomFileList) {\n" +
                                        "\n" +
                                        "  Process p4 = \"git --git-dir=/fs/ENGINEERING_DIR/pi_git_repos/\${GIT_REPO}/.git --work-tree=/fs/ENGINEERING_DIR/pi_git_repos/\${GIT_REPO}/ show remotes/origin/\${GIT_BRANCH}:\${pomFile}\".execute()\n" +
                                        " \n" +
                                        " pomXml=new XmlSlurper().parseText(p4.getText())\n" +
                                        " \n" +
                                        "   pomGid = pomXml.groupId\n" +
                                        "   pomAid = pomXml.artifactId\n" +
                                        "  \n" +
                                        "   if ( pomGid == \"\" ){\n" +
                                        "      pomGid = pomXml.parent.groupId\n" +
                                        "    }\n" +
                                        "   \n" +
                                        "    moduleList.add(pomGid.text() + \":\" + pomAid.text())\n" +
                                        "\n" +
                                        "}\n" +
                                        "moduleList.removeAll{it.contains(\"groupId\")}\n" +
                                        "return moduleList.sort()")
                                fallbackScript ("")
                            }
                        }
                        choiceType('PT_MULTI_SELECT')
                        referencedParameters('GIT_REPO,GIT_BRANCH')
                        filterable(false)
                    }

                    stringParam {
                        name('testPattern')
                        defaultValue('')
                        description('Use this to run specifc test or tests (using patterns). \n' +
                                'For Example - MySimpleTest or My SimpleTest#testMethod1 or My*Tests')
                    }
                    stringParam {
                        name('FStestPattern')
                        defaultValue('')
                        description('')
                    }
                }
            }

            sidebarLinks {
                // use built-in image
                link(link2Workspace, 'WORKSPACE', '/static/dba52d9a/images/24x24/folder.png')
            }
            envInjectJobProperty {
                info {
                    propertiesFilePath('./workspace.properties')
                    propertiesContent('')
                    scriptFilePath('')
                    scriptContent("echo \'Checkout property file before SCM\' && curl -k -X GET -u GITHUB_USER:GITHUB_PASSWORD https://GITHUB_HOST/raw/${fullRepoName}/${branchName}/workspace.properties > ./workspace.properties && ls -la ./workspace.properties \n" +
                            " if grep -q  ArtifactsToDeploy= ./workspace.properties ; then \n" +
                            "         echo ArtifactsToDeploy exists ; \n " +
                            " else " +
                            " echo ArtifactsToDeploy=XXXXXXX >>./workspace.properties ; \n" +
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

        //logRotator(-1, 10)
        jdk('jdk_parameter')
        multiscm{
            gitSCM{
                userRemoteConfigs {
                    userRemoteConfig {
                        // Specify the URL of this remote repository.
                        url(projectGitSshUrlToRepo.toString())

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
                gitTool('Git_1.7_x64')
                doGenerateSubmoduleConfigurations(false)
                extensions{
                    localBranch{
                        localBranch(branchName)
                    }
                }
                browser{}
                extensions{
                    wipeWorkspace()
                    cleanBeforeCheckout()
                }
            }

        }

        wrappers {
            timestamperBuildWrapper()
            buildUserVars()
            PRODUCT1dentialsBinding{
                usernamePasswordMultiBinding  {
                    usernameVariable('ArtifactoryARTIFACTORY_USERUser')
                    passwordVariable('ArtifactoryARTIFACTORY_USERPassword')
                    PRODUCT1dentialsId('ARTIFACTORY_USER')
                }
            }

            maskPasswordsBuildWrapper{
                varPasswordPairs{
                    varPasswordPair {
                        var("ARTIFACTORY_USER")
                        password("wfnjwnfi99r3kenfuw++njansdn=msk=")
                    }
                }
            }
        }

        preBuildSteps {
            shell("echo 'Checkout settings.xml ' \n" +
                    "   curl -k -X GET -u GITHUB_USER:GITHUB_PASSWORD https://GITHUB_HOST/raw/RE/PipeLines/master/config/settings.xml > \${WORKSPACE}/settings.xml \n" +
                    "   ls -la \${WORKSPACE}/settings.xml \n" +
                    "   sed -i 's/VIRTUAL-REPO/${branchName}-virt/g' \${WORKSPACE}/settings.xml \n" +
                    "   cat \${WORKSPACE}/settings.xml \n" +
//                    "   curl -v --user \${ArtifactoryARTIFACTORY_USERUser}:\${ArtifactoryARTIFACTORY_USERPassword}  -T \${WORKSPACE}/settings.xml -X PUT \"http://ARTIFACTORY_HOST:8081/artifactory/${branchName}-local/settings.xml\" \n" +
                    "   curl -v --user ARTIFACTORY_USER:\${ARTIFACTORY_USER}  -T \${WORKSPACE}/settings.xml -X PUT \"http://ARTIFACTORY_HOST:8081/artifactory/${branchName}-local/settings.xml\" \n" +
                    "   git checkout ${branchName} \n" +
                    "   git add settings.xml \n" +
                    "   git commit -m \"add settings.xml\" || true \n" +
                    "   git push")
            systemGroovyCommand("import hudson.model.*\n" +
                    "import jenkins.model.*;\n" +
                    "import static groovy.io.FileType.*\n" +
                    "\n" +
                    "def mvnModules = \"\"\n" +
                    "def mvnProfiles = \"\"\n" +
                    "def mvnTestsPattern = \"\"\n" +
                    "def mvnFSTestsPattern = \"\"\n" +
                    "\n" +
                    "//def curJob=jenkins.model.Jenkins.instance.getItem(this.binding.build.project.name)\n" +
                    "String jobName = (build.getEnvironment(listener).get('JOB_NAME') ?: \"NOJOB\")\n" +
                    "def curJob = Jenkins.instance.getItemByFullName(jobName.toString());\n" +
                    "def curBuild=curJob.getLastBuild()\n" +
                    "def curBuildEnvVars=curBuild.getEnvVars()\n" +
                    "def JobSelectedModules=curBuildEnvVars['Modules']\n" +
                    "def JobSelectedProfiles=curBuildEnvVars['Profiles']\n" +
                    "def JobSelectedTests=curBuildEnvVars['testPattern']\n" +
                    "def JobSelectedFSTests=curBuildEnvVars['FStestPattern']\n" +
                    "println  \"DESCRIPTION: \"+ curBuildEnvVars['BUILD_USER_ID'] + \"-\"+ curBuildEnvVars['JOB_NAME']\n" +
                    "\n" +
                    "if  (JobSelectedModules != \"\") {\n" +
                    "println \"**********  MODULES = \" + JobSelectedModules + \"  **********\"\n" +
                    "mvnModules = \"-pl \" + JobSelectedModules\n" +
                    "  \n" +
                    "}\n" +
                    "\n" +
                    "if  (JobSelectedProfiles != \"\") {\n" +
                    "println \"**********  PROFILES = \" + JobSelectedProfiles + \"  **********\"\n" +
                    "mvnProfiles = \"-P\" + JobSelectedProfiles\n" +
                    "  \n" +
                    "}\n" +
                    "if  (JobSelectedTests != \"\") {\n" +
                    "println \"**********  TESTS TO EXECUTE = \" + JobSelectedTests + \"  **********\"\n" +
                    "\n" +
                    "  mvnTestsPattern = \"-DfailIfNoTests=false -Dtest=\" + JobSelectedTests\n" +
                    "}\n" +
                    "if  (JobSelectedFSTests != \"\") {\n" +
                    "println \"********** FS TESTS TO EXECUTE = \" + JobSelectedFSTests + \"  **********\"\n" +
                    "\n" +
                    "  mvnFSTestsPattern = \"-DfailIfNoTests=false -Dit.test=\" + JobSelectedFSTests\n" +
                    "  \n" +
                    "}\n" +
                    "\n" +
                    "\n" +
                    "  // Add build environment variables\n" +
                    "///////////////////////////////////////////////////////////////////////////////\n" +
                    "    \n" +
                    "    def pl = new ArrayList<StringParameterValue>()\n" +
                    "    pl.add(new StringParameterValue(\"MAVEN_MODULES_TO_RUN\", mvnModules.toString()))\n" +
                    "    pl.add(new StringParameterValue(\"MAVEN_PROFILES_TO_RUN\", mvnProfiles.toString()))\n" +
                    "\tpl.add(new StringParameterValue(\"MAVEN_TESTS_TO_RUN\", mvnTestsPattern.toString()))\n" +
                    "\tpl.add(new StringParameterValue(\"MAVEN_FS_TESTS_TO_RUN\", mvnFSTestsPattern.toString()))\n" +
                    "    def newParams = null\n" +
                    "    def oldParams = curBuild.getAction(ParameteORGction.class)\n" +
                    "    \n" +
                    "    if(oldParams != null) {\n" +
                    "      newParams = oldParams.createUpdated(pl)\n" +
                    "      curBuild.actions.remove(oldParams)\n" +
                    "    } else {\n" +
                    "      newParams = new ParameteORGction(pl)\n" +
                    "    }\n" +
                    "    \n" +
                    "    curBuild.addAction(newParams)") {
            }
        }
        archivingDisabled(true)
        siteArchivingDisabled(true)
        inPRODUCT1mentalBuild(false)
        fingerprintingDisabled(true)
        disableDownstreamTrigger(true)
        goals('\${GOALS}  \${Debug} \${MAVEN_PROFILES_TO_RUN} \${MAVEN_MODULES_TO_RUN} \${MAVEN_TESTS_TO_RUN} \${MAVEN_FS_TESTS_TO_RUN} -s settings.xml')
        mavenOpts('\${MAVEN_OPTS}')
        mavenInstallation('mvn_parameter')
        localRepository(LocalRepositoryLocation.LOCAL_TO_WORKSPACE)
        publishers{
            //buildDescription('DESCRIPTION: (.*)', '', '', '', false)
            //			Deploy to Artifactory
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
                        keyFromSelect(branchNameLocal)
                        dynamicMode(false)
                    }
                    deploySnapshotRepository{
                        keyFromText('null')
                        keyFromSelect(branchNameLocal)
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
                    excludePatterns('*password*,*sePRODUCT1t*')
                }
            }
        }
    }
}

//                          *********     PRODUCT2            *********

def createMavenJob_DSL_PRODUCT2_PATCH_SET(List args){
    createMavenJob_DSL_PRODUCT2(args)
    createMavenJob_DSL_PRODUCT2_RC(args)
}

def createMavenJob_DSL_PRODUCT2(List args){

    def ciJobName = args[0]
    def projectGitSshUrlToRepo = args[1]
    def branchName = args[2]
    def PRODUCT = args[3]
    def fullRepoName = args[4]

    String productFolder = PRODUCT + "/Patches"
    String patchesFolder = PRODUCT + "/Patches/"  + branchName

    FolderDSL.createFolder_DSL(this, productFolder)
    FolderDSL.createFolder_DSL(this, patchesFolder)


    def maven_repo_local="/workspace/workspace/." + branchName +"_repo"
    // propertyFile is injected into Job Environments
    def branchNameLocal = branchName + "-local"
    def link2Workspace = "http://WEB_HOST/new_workspace/" + PRODUCT + "/Patches/" + branchName + "/" + ciJobName

    def mavenJob_dsl_PRODUCT2 = mavenJob("${patchesFolder}/${ciJobName}") {}

    MavenJobTemplates.setDisabled(mavenJob_dsl_PRODUCT2,[false])
    MavenJobTemplates.setLabel(mavenJob_dsl_PRODUCT2,["LABEL_${PRODUCT}"])
    MavenJobTemplates.setJdk(mavenJob_dsl_PRODUCT2,['jdk1.8_045_linux'])
    MavenJobTemplates.addDescription(mavenJob_dsl_PRODUCT2,[branchName])
    MavenJobTemplates.sidebarLinks(mavenJob_dsl_PRODUCT2,[link2Workspace])
    MavenJobTemplates.properties_buildDiscarder(mavenJob_dsl_PRODUCT2,['10'])
    MavenJobTemplates.multiscm_gitSCM(mavenJob_dsl_PRODUCT2, [projectGitSshUrlToRepo.toString(), branchName, 'Git_1.7_x64'])
    MavenJobTemplates.wrappers_timestamperBuildWrapper(mavenJob_dsl_PRODUCT2)
    MavenJobTemplates.triggers_SCM(mavenJob_dsl_PRODUCT2,['*/10 * * * *',false])

    // Build

    MavenJobTemplates.setArchivingDisabled(mavenJob_dsl_PRODUCT2,[true])
//    MavenJobTemplates.setSiteArchivingDisabled(mavenJob_dsl_PRODUCT2,[true])
    MavenJobTemplates.setFingerprintingDisabled(mavenJob_dsl_PRODUCT2,[true])
    MavenJobTemplates.setDisableDownstreamTrigger(mavenJob_dsl_PRODUCT2)
    MavenJobTemplates.setBlockOnDownstreamProjects(mavenJob_dsl_PRODUCT2)
    MavenJobTemplates.setGoals(mavenJob_dsl_PRODUCT2,['clean install'])
    MavenJobTemplates.setMavenOpts(mavenJob_dsl_PRODUCT2,['-XX:MaxPermSize=512m -XX:PermSize=512m -Xms512m -Xmx2560m -XX:-UseGCOverheadLimit -client -Dmaven.test.failure.ignore=false'])
    MavenJobTemplates.setMavenInstallation(mavenJob_dsl_PRODUCT2,['mvn_3.3.9'])
    MavenJobTemplates.setLocalRepository(mavenJob_dsl_PRODUCT2,[LocalRepositoryLocation.LOCAL_TO_WORKSPACE])
    MavenJobTemplates.setMvnSettingsProvider(mavenJob_dsl_PRODUCT2,['cfcb170c-3868-45d7-a30a-99e55747e56b'])
    MavenJobTemplates.setMvnGlobalSettingsProvider(mavenJob_dsl_PRODUCT2,['03e9a980-bf05-44af-9491-3563ca389ef2'])

    // POST STEPS
//    MavenJobTemplates.postSteps_ArtifactARTIFACTORY_USER(mavenJob_dsl_PRODUCT2,['**/target/*.*ar','/fs/artifacts/\${JOB_NAME}/\${BUILD_NUMBER}/\${GIT_COMMIT}'])

    // POST BUILD
    // Publishers
    MavenJobTemplates.publishers_ArtifactoryRedeployPublisher(mavenJob_dsl_PRODUCT2,[branchNameLocal,'libs-snapshot-local',false])
    MavenJobTemplates.publishers_DescriptionSetterPublisher(mavenJob_dsl_PRODUCT2,['CL-\${GIT_COMMIT}'])
    //MavenJobTemplates.publishers_DiscardBuildPublisher(mavenJob_dsl_PRODUCT2,[])
    MavenJobTemplates.publishers_ArtifactARTIFACTORY_USERPublisher(mavenJob_dsl_PRODUCT2,['**/target/*.*ar','/fs/artifacts/\${JOB_NAME}/\${BUILD_NUMBER}/\${GIT_COMMIT}',false])
    MavenJobTemplates.publishers_ExtendedEmailPublisher(mavenJob_dsl_PRODUCT2,['\$DEFAULT_RECIPIENTS,'])
}

def createMavenJob_DSL_PRODUCT2_RC(List args){

    def ciJobName = args[0] + "_RC"
    def projectGitSshUrlToRepo = args[1]
    def branchName = args[2]
    def PRODUCT = args[3]
    def fullRepoName = args[4]

    String productFolder = PRODUCT + "/Patches"
    String patchesFolder = PRODUCT + "/Patches/"  + branchName

    FolderDSL.createFolder_DSL(this, productFolder)
    FolderDSL.createFolder_DSL(this, patchesFolder)


    def maven_repo_local="/workspace/workspace/." + branchName +"_repo"
    // propertyFile is injected into Job Environments
    def branchNameLocal = branchName + "-local"
    def link2Workspace = "http://WEB_HOST/new_workspace/" + PRODUCT + "/Patches/" + branchName + "/" + ciJobName

    def mavenJob_dsl_PRODUCT2 = mavenJob("${patchesFolder}/${ciJobName}") {

        properties {
            promotions {
                promotion {
                    name('Release')
                    icon("star-gold")
                    conditions {
                        manual('')
                    }
                    actions {
                        shell("curl -k -X GET -u GITHUB_USER:GITHUB_PASSWORD https://GITHUB_HOST/raw/RE/JenkinsTools/master/src/groovy/publishCRArtifacts.groovy > ./publishCRArtifacts.groovy && ls -la ./publishCRArtifacts.groovy")
                        systemGroovyScriptFile("publishCRArtifacts.groovy") {
//                            binding(String name, String value)
                            classpath("\${WORKSPACE}")
                        }
                    }
                }
            }
        }
    }

    MavenJobTemplates.preSteps_proxyBuilder(mavenJob_dsl_PRODUCT2,["Git.Export.Env.ShortSHA.Groovy"])
    MavenJobTemplates.preSteps_proxyBuilder(mavenJob_dsl_PRODUCT2,["ReleaseIt.Snapshot.To.Next.Build.Number.Groovy"])
    MavenJobTemplates.setDisabled(mavenJob_dsl_PRODUCT2,[false])
    MavenJobTemplates.setLabel(mavenJob_dsl_PRODUCT2,["LABEL_${PRODUCT}"])
    MavenJobTemplates.setJdk(mavenJob_dsl_PRODUCT2,['jdk1.8_045_linux'])
    MavenJobTemplates.addDescription(mavenJob_dsl_PRODUCT2,[branchName])
    MavenJobTemplates.sidebarLinks(mavenJob_dsl_PRODUCT2,[link2Workspace])
    MavenJobTemplates.properties_buildDiscarder(mavenJob_dsl_PRODUCT2,['10'])
    MavenJobTemplates.multiscm_gitSCM(mavenJob_dsl_PRODUCT2, [projectGitSshUrlToRepo.toString(), branchName, 'Git_1.7_x64'])
    MavenJobTemplates.wrappers_timestamperBuildWrapper(mavenJob_dsl_PRODUCT2)
    MavenJobTemplates.triggers_SCM(mavenJob_dsl_PRODUCT2,['*/10 * * * *',false])

    // Build

    MavenJobTemplates.setArchivingDisabled(mavenJob_dsl_PRODUCT2,[true])
//    MavenJobTemplates.setSiteArchivingDisabled(mavenJob_dsl_PRODUCT2,[true])
    MavenJobTemplates.setFingerprintingDisabled(mavenJob_dsl_PRODUCT2,[true])
    MavenJobTemplates.setDisableDownstreamTrigger(mavenJob_dsl_PRODUCT2)
    MavenJobTemplates.setBlockOnDownstreamProjects(mavenJob_dsl_PRODUCT2)
    MavenJobTemplates.setGoals(mavenJob_dsl_PRODUCT2,['clean install  -P master -Denvironment=master'])
    MavenJobTemplates.setMavenOpts(mavenJob_dsl_PRODUCT2,['-XX:MaxPermSize=512m -XX:PermSize=512m -Xms512m -Xmx2560m -XX:-UseGCOverheadLimit -client -Dmaven.test.failure.ignore=false'])
    MavenJobTemplates.setMavenInstallation(mavenJob_dsl_PRODUCT2,['mvn_3.3.9'])
    MavenJobTemplates.setLocalRepository(mavenJob_dsl_PRODUCT2,[LocalRepositoryLocation.LOCAL_TO_WORKSPACE])
    MavenJobTemplates.setMvnSettingsProvider(mavenJob_dsl_PRODUCT2,['cfcb170c-3868-45d7-a30a-99e55747e56b'])
    MavenJobTemplates.setMvnGlobalSettingsProvider(mavenJob_dsl_PRODUCT2,['03e9a980-bf05-44af-9491-3563ca389ef2'])

    // POST STEPS
//    MavenJobTemplates.postSteps_ArtifactARTIFACTORY_USER(mavenJob_dsl_PRODUCT2,['**/target/*.*ar','/fs/artifacts/\${JOB_NAME}/\${BUILD_NUMBER}/\${GIT_COMMIT}'])

    // POST BUILD
    // Publishers
    MavenJobTemplates.publishers_ArtifactoryRedeployPublisher(mavenJob_dsl_PRODUCT2,[branchNameLocal,'libs-snapshot-local',false])
    MavenJobTemplates.publishers_DescriptionSetterPublisher(mavenJob_dsl_PRODUCT2,['CL-\${GIT_COMMIT}'])
    //MavenJobTemplates.publishers_DiscardBuildPublisher(mavenJob_dsl_PRODUCT2,[])
    MavenJobTemplates.publishers_ArtifactARTIFACTORY_USERPublisher(mavenJob_dsl_PRODUCT2,['**/target/*.*ar','/fs/artifacts/\${JOB_NAME}/\${BUILD_NUMBER}/\${GIT_COMMIT}',false])
    MavenJobTemplates.publishers_ExtendedEmailPublisher(mavenJob_dsl_PRODUCT2,['\$DEFAULT_RECIPIENTS,'])

}

//                          *********     PRODUCT3            *********
def createMavenJob_DSL_PRODUCT3(def args){
    //createMavenJob_DSL_PRODUCT2(args)
}

def createMavenJob_DSL_PRODUCT4(def args){

    def ciJobName = args[0]
    def projectGitSshUrlToRepo = args[1]
    def branchName = args[2]
    def PRODUCT = args[3]
    def fullRepoName = args[4]

    String productFolder = PRODUCT + "/Patches"
    String patchesFolder = PRODUCT + "/Patches/"  + branchName

    FolderDSL.createFolder_DSL(this, productFolder)
    FolderDSL.createFolder_DSL(this, patchesFolder)

    def maven_repo_local="/workspace/workspace/." + branchName +"_repo"
    // propertyFile is injected into Job Environments
    def branchNameLocal = branchName + "-local"
    def link2Workspace = "http://WEB_HOST/new_workspace/" + PRODUCT + "/Patches/" + branchName + "/" + ciJobName

    def mavenJob_dsl_PRODUCT4 = mavenJob("${patchesFolder}/${ciJobName}") {}

    MavenJobTemplates.setDisabled(mavenJob_dsl_PRODUCT4,[false])
    MavenJobTemplates.setLabel(mavenJob_dsl_PRODUCT4,["LABEL_${PRODUCT}"])
    MavenJobTemplates.sidebarLinks(mavenJob_dsl_PRODUCT4,[link2Workspace])
    MavenJobTemplates.setJdk(mavenJob_dsl_PRODUCT4,['jdk_parameter'])
    MavenJobTemplates.multiscm_gitSCM(mavenJob_dsl_PRODUCT4, [projectGitSshUrlToRepo.toString(), branchName, 'Git_1.7_x64'])
    MavenJobTemplates.wrappers_PRODUCT1dentialsBinding(mavenJob_dsl_PRODUCT4,['ArtifactoryARTIFACTORY_USERUser','ArtifactoryARTIFACTORY_USERPassword','ARTIFACTORY_USER'])
    MavenJobTemplates.wrappers_MaskPasswordsBuildWrapper(mavenJob_dsl_PRODUCT4,["ARTIFACTORY_USER","wfnjwnfi99r3kenfuw++njansdn=msk="])
    MavenJobTemplates.wrappers_NpmPackagesBuildWrapper(mavenJob_dsl_PRODUCT4,['nodejs6.9.5_linux'])
    MavenJobTemplates.configure_APPEND_MaskPasswordsBuildWrapper(mavenJob_dsl_PRODUCT4,["rpmARTIFACTORY_USERpwd","6iSPtP+V6/4zzPbhnPckYdmrcb4c3w1i1zKbdNBZlYg="])
    MavenJobTemplates.preSteps_Shell(mavenJob_dsl_PRODUCT4,["env \n" +
                                                                 "echo 'Checkout settings.xml ' \n" +
                                                                 "   curl -k -X GET -u GITHUB_USER:GITHUB_PASSWORD https://GITHUB_HOST/raw/RE/PipeLines/master/config/settings.xml > \${WORKSPACE}/settings.xml \n" +
                                                                 "   ls -la \${WORKSPACE}/settings.xml \n" +
                                                                 "   sed -i 's/VIRTUAL-REPO/${branchName}-virt/g' \${WORKSPACE}/settings.xml \n" +
                                                                 "   cat \${WORKSPACE}/settings.xml \n" +
                                                                 "   curl -v --user \${ArtifactoryARTIFACTORY_USERUser}:\${ArtifactoryARTIFACTORY_USERPassword}  -T \${WORKSPACE}/settings.xml -X PUT \"http://ARTIFACTORY_HOST:8081/artifactory/${branchName}-local/settings.xml\" \n" +
                                                                 "   git checkout ${branchName} \n" +
                                                                 "   git add settings.xml \n" +
                                                                 "   git commit -m \"add settings.xml\" || true \n" +
                                                                 "   git push"])
    MavenJobTemplates.setArchivingDisabled(mavenJob_dsl_PRODUCT4,[true])
    MavenJobTemplates.setSiteArchivingDisabled(mavenJob_dsl_PRODUCT4,[true])
    MavenJobTemplates.setFingerprintingDisabled(mavenJob_dsl_PRODUCT4,[true])
    MavenJobTemplates.setrunHeadless(mavenJob_dsl_PRODUCT4,[true])

    MavenJobTemplates.properties_feature_environment(mavenJob_dsl_PRODUCT4,[fullRepoName, branchName])
    MavenJobTemplates.setGoals(mavenJob_dsl_PRODUCT4,['\${GOALS} -s settings.xml'])
    MavenJobTemplates.configure_Settings(mavenJob_dsl_PRODUCT4,['\${WORKSPACE}/settings.xml'])
    MavenJobTemplates.configure_GlobalSettings(mavenJob_dsl_PRODUCT4,['\${WORKSPACE}/settings.xml'])
    MavenJobTemplates.setMavenOpts(mavenJob_dsl_PRODUCT4,['\${MAVEN_OPTS}'])
    MavenJobTemplates.setMavenInstallation(mavenJob_dsl_PRODUCT4,['mvn_parameter'])
    MavenJobTemplates.setLocalRepository(mavenJob_dsl_PRODUCT4,[LocalRepositoryLocation.LOCAL_TO_WORKSPACE])
    MavenJobTemplates.postSteps_Shell_BY_STATUS(mavenJob_dsl_PRODUCT4,['SUCCESS',"#!/bin/bash\n" +
            "count=`find \"\${WORKSPACE}/packager/target/rpm/\" -name \"*.rpm\" | wc -l`\n" +
            "if [ \"\$count\" -gt \"0\" ]; then\n" +
            "   find \"\${WORKSPACE}/packager/target/rpm/\" -name \"*.rpm\" -exec curl -u \${ArtifactoryARTIFACTORY_USERUser}:\${ArtifactoryARTIFACTORY_USERPassword} -XPUT \"http://ARTIFACTORY_HOST:8081/artifactory/${branchName}-yum/\" -T '{}' \\;\n" +
            "fi"])
    MavenJobTemplates.publishers_DiscardBuildPublisher(mavenJob_dsl_PRODUCT4,[])
    MavenJobTemplates.publishers_ExtendedEmailPublisher(mavenJob_dsl_PRODUCT4,[])
    MavenJobTemplates.publishers_ArtifactoryRedeployPublisher(mavenJob_dsl_PRODUCT4,[branchNameLocal, branchNameLocal, false])
}





package artifactory

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import jenkins.model.*

String jobName = (build.getEnvironment(listener).get('JOB_NAME') ?: "NOJOB")
def curJob = Jenkins.instance.getItemByFullName(jobName.toString())
def curBuild=curJob.getLastBuild()
def curBuildEnvVars=curBuild.getEnvVars()
def FILTER = curBuildEnvVars['FILTER'] ?: "ftr-"
def PRODUCT = curBuildEnvVars['PRODUCT']

println "\n\n   **************************  MANAGE ARTIFACTORY REPOSITORIES   **************************\n"

featuresFromSeedJobs = getFeaturesFromSeed(FILTER, curJob)
//featuresFromSeedJobs.each{ println "features from seed : " + it}
featuresFromArtifactoryByProduct = getFeaturesFromArtifactory(FILTER, PRODUCT)

//println  "\n      ***************   INTERSECTION --> COMMON  ********************\n"
def common = featuresFromSeedJobs.intersect(featuresFromArtifactoryByProduct)

//println  "\n      ***************   REMOVE COMMON OBJECTS  ********************\n"
common.each{
    featuresFromSeedJobs.remove(it)
    featuresFromArtifactoryByProduct.remove(it)
}

println "\n      ***************   REPORT BEFORE EXECUTION  ********************\n"
println "\n  -->> Common features for Seed and Artifactory :" + common + "\n"
println "\n  -->> Add Repositories to Artifactory :" +  featuresFromSeedJobs + "\n"
println "\n  -->> Remove Repositories from Artifactory :" +  featuresFromArtifactoryByProduct + "\n"

println  "\n      ***************   EXECUTION  ********************\n"
println  "\n      ***************   MAVEN REPOSITORIES********************\n"
createArtifactoryLocalRepositories(featuresFromSeedJobs, PRODUCT)
createArtifactoryVirtualRepositories(featuresFromSeedJobs, PRODUCT)
deleteArtifactoryLocal_Repositories(featuresFromArtifactoryByProduct, PRODUCT)
deleteArtifactoryVirtual_Maven_Repositories(featuresFromArtifactoryByProduct)



//  *****************************    METHODS   ************************************

def getFeaturesFromSeed(def FILTER, def curJob){
//    println("XXXX" + this.binding.build.project.name)
//    def curJob=jenkins.model.Jenkins.instance.getItem(this.binding.build.project.name)
//
    def curBuild=curJob.getLastBuild()
    def featuresMap = [:]

    generatedJobsBuildActions = curBuild.getAction(javaposse.jobdsl.plugin.actions.GeneratedJobsBuildAction)
    generatedJobsBuildActions.getItems().eachWithIndex { item, index ->
        ftrNameSeedFull = generatedJobsBuildActions.getItems()[index].getName()
        if ((m = ftrNameSeedFull =~ /(.*)?($FILTER.*)(-local|$)/)) {
            def ftrNameSeed = m.group(2)
            //println "FULL PATCH NAME " + ftrNameSeedFull
            //println "MATCH=$ftrNameSeed"
            featuresMap."$ftrNameSeed" = "1"
        }
    }

    println "-->> Features from Seed Jobs" : featuresMap.keySet()
    return featuresMap.keySet()
}

def getFeaturesFromArtifactory(def FILTER, def PRODUCT){
    ftrNameArtifactoryMap=[:]
    def artQ = 'curl -k  -X GET http://ARTIFACTORY_HOST:8081/artifactory/api/repositories?type=local'.execute()
    def  repoList=new JsonSlurper().parseText(artQ.getText())

    repoList.each { repo ->
        def repoName = repo.key
        def repoDescription = repo.description
        //if ((m = repoName =~ /(.*)?($FILTER.*)(-local)/) || (m = repoName =~ /(.*)?($FILTER.*)(-yum)/)) {
        if ((m = repoName =~ /(^$FILTER.*)(-local)/) || (m = repoName =~ /(^$FILTER.*)(-yum)/)) {

            def ftrNameArtifactory = m.group(1)
            println( "ZZZZZZZZZZZZZZZZZ : " + ftrNameArtifactory)
            //if ((m = repoDescription =~ /(.*)?($PRODUCT.*)/)) {
            if ((m = repoDescription =~ /^${PRODUCT}$/)){
                println "m" + m
                println "repoName " + repoName
                println "repoDescription " + repoDescription
                //println "MATCH=$ftrNameArtifactory"
                //println "Feature Name from Artifactory : " + ftrNameArtifactory
                ftrNameArtifactoryMap."$ftrNameArtifactory" = "1"
            }
        }
    }
    println "  -->> Features from Artifactory" : ftrNameArtifactoryMap.keySet()
    return ftrNameArtifactoryMap.keySet()
}

def sendQueryToArtifactory(def urlString, def jsonQuerySearch, def conType){
    def url = new URL(urlString)
    println "URL is : " + urlString
    def connection = url.openConnection()
    connection.setRequestMethod(conType)
    def basicAuth = "ARTIFACTORY_USER:ARTIFACTORY_PASSWORD".getBytes().encodeBase64().toString()
    connection.setRequestProperty("Authorization", "Basic ${basicAuth}")
    connection.setRequestProperty("Content-Type", "application/json");
//	connection.setRequestProperty("Accept", "application/json");
    if(conType != "DELETE"){
        connection.doOutput = true
        def writer = new OutputStreamWriter(connection.outputStream)
        writer.write(jsonQuerySearch.toString())
        writer.flush()
        writer.close()
    }
    connection.connect()
//	println connection.responseCode
//	headerFields = connection.getHeaderFields()
//	headerFields.each {println it}
//  println connection.getResponseMessage()

    if(conType == "DELETE") {
        assert ((connection.responseCode == 200) || (connection.responseCode == 404))
    }else if(conType == "PUT"){
        assert ((connection.responseCode == 200) || (connection.responseCode == 400))
    }else {
        assert connection.responseCode == 200
    }
}

//                           CRUD
//                  create REPOSITORIES
//      LOCAL REPOSITORIES

// By product and package type
def createArtifactoryLocalRepositories(def featuresRepositories, def product){
    println " \n\n\n  ------------------------------  \n" +
            " create Artifactory Repositories \n"

    featuresRepositories.each{
        createArtifactoryLocalRepository(it, product, "maven", "-local","maven-2-default")
        if(product.equals('PRODUCT4')){
            createArtifactoryLocalRepository(it, product, "yum","-yum","simple-default")
        }
    }
    println "\n  ------------------------------  \n"
}

def createArtifactoryLocalRepository(def repo, def product, def packageType, def repoSuffix, def repoLayoutRef){
    def jsonQuerySearch = new JsonBuilder()
    def repoName= repo + repoSuffix
    println "Feature repo : " + repoName
    jsonQuerySearch(
            key: "${repoName}",
            rclass : "local",
            repoLayoutRef : "${repoLayoutRef}",
            packageType: "${packageType}",
            description: "${product}"
    )
    urlString="http://ARTIFACTORY_HOST:8081/artifactory/api/repositories/${repoName}?pos=2"
    sendQueryToArtifactory(urlString,jsonQuerySearch,"PUT")
}


//     VIRTUAL REPOSITORIES
// By product and package type
def createArtifactoryVirtualRepositories(def featuresRepositories, def product){
    println "\n\n\n------------------------------   \n" +
            "create Virtual Artifactory Repositories \n"
    featuresRepositories.each{
        createArtifactoryVirtualRepository(it, product, "maven")
    }
    println "\n  ------------------------------  \n"
}

def createArtifactoryVirtualRepository(def repo, def product, def packageType){
    def jsonQuerySearch = new JsonBuilder()
    def repoName = repo + "-virt"
    def localRepoName = repo + "-local"
    println "Virtual Feature repo : " + repoName
    println "Local in Virtual  Feature repo : " + repoName
    jsonQuerySearch(
            key: "${repoName}",
            rclass : "virtual",
            repositories: ["${localRepoName}","libs-gradle-local", "ext-snapshot-local", "remote-repos", "bw-libs-snapshot", "libs-snapshot-local"],
            packageType: "${packageType}",
            description: "${product}"
    )
    def urlString="http://ARTIFACTORY_HOST:8081/artifactory/api/repositories/${repoName}?pos=2"
    sendQueryToArtifactory(urlString,jsonQuerySearch,"PUT")
}


//    DELETE REPOSITORIES
def deleteArtifactoryLocal_Repositories(def featureRepo, def product){
    println "\n\n\n------------------------------   \n" +
            "Delete Local (Maven) Artifactory Repositories \n"
    def jsonQuerySearch = new JsonBuilder()
    featureRepo.each{
        //def repoName= it + "-local"
        deleteArtifactory_Repository(it + "-local")

        if(product.equals('PRODUCT4')){
            deleteArtifactory_Repository(it + "-yum")
        }
    }
    println "\n  ------------------------------  \n"
}

def deleteArtifactoryVirtual_Maven_Repositories(def featureRepo){
    println "\n\n\n------------------------------   \n" +
            "Delete Virtual Artifactory Repositories \n"
    featureRepo.each {
        deleteArtifactory_Repository(it + "-virt")
    }
    println "\n  ------------------------------  \n"
}


def deleteArtifactory_Repository(def repo){
    def jsonQuerySearch = new JsonBuilder()
    println "Delete Repository : " + repo
    def urlString="http://ARTIFACTORY_HOST:8081/artifactory/api/repositories/${repo}"
    sendQueryToArtifactory(urlString,jsonQuerySearch,"DELETE")
}


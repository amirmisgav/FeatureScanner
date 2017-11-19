## FeatureScanner


`FeatureScanner` creates build environments automatically for feature or patch branches of multi components developed products.<br>
This tool helps developers/testers to creates a private build environment that include Jenkins pipeline, project for each component and a private Artifactory repository for each feature/patch.<br>
Once GitHub repository branches are created by the developer, his build environment is ready. Once the branches are removed, the environment(pipeline,jobs,artifactory repositoy) is deleted.<br>
FeatureScanner scans GitHub repositories according the repositories in config/products_maven.properties or in config/products_freeStyle.properties.
This project also demonstrates how to manage pipelines in one place.It means Jenkinsfiles are not included in each Git repository. Jenkinsfiles are included in this project and created by DSL plugin of Jenkins for each feature/patch.
This implementation create pipeline that runs regular projects/jobs for each component. You can choose to run only pipelines instead. I implement this way because of certain obligations i met.
Jenkins Objects are created and manged by DSL Plugin. Artifactory is managed by src/artifactory/ArtifactoryManager.groovy .
This tool scans only branches with certain prefix. Default prefix is "ftr-" and may be change if parameter FILTER is set in Jenkins generatot job. watch attached screenshot.<br>

#### Prerequisites
1. Clone repository: `git@github.com:amirmisgav/FeatureScanner.git`
2. Install DSL plugin on your Jenkins.
3. Find  Replace the following Parameters to real values:<br>
    *GITHUB_HOST ,GITHUB_USER ,GITHUB_PASSWORD ,WEB_HOST* ,<br>
    *ARTIFACTORY_HOST ,ARTIFACTORY_USER ,ARTIFACTORY_PASS* ,<br>
    *PRODUCT1 ,PRODUCT2 ,PRODUCT3 ,...* <br>
    *COMPONENT1 , COMPONENT2 , COMPONENT3 ,COMPONENT4* ,
    ...<br>
4.  Change the dsl closures in methods createMavenJob_DSL_in FeatureGenerator.groovy according the needs of the build. <br>
    **Note** that a lot of the dsl closures were encapsulated inside template methods in *src/generators/templates/MavenJobTemplates.groovy* and in */src/generators/templates/ProjectTemplates.groovy*.
5.  Create Jenkins project that will function as a Job Generator: Checkout this project and runs dsl script: *src/generators/FeatureGenerator.groovy* or */src/generators/PatchGenerator.groovy* according your needs. In addition this job will run also in another step groovy: src/artifactory/ArtifactoryManager.groovy. This job should be schedule to run every a few minutes. Watch attched screenshots.<br>


<br>
![Filter](images/filter.png?raw=true)
<br>
![Git Url](images/repository_url.png?raw=true)
<br>
![Scheduler](images/scheduler.png?raw=true)
<br>
![Proccess Job DSL](images/Proccess_job_DSL.png?raw=true)
<br>


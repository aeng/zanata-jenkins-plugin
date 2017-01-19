This plugin will help to synchronize between SCM repository and Zanata. 

Once you install this jenkins plugin, you can set up a job to perform typical localization workflow:
- check out from SCM
- push source (and/or translation) to Zanata server
- pull translation from Zanata server
- commit translation into SCM (currently only git is supported automatically. Other SCM will need to use scripting)
- push the commit back to remote SCM (using git publisher or equivalent)

### How to build and test locally
need to install a specific version of zanata client before next Zanata release. 
Check out any branch (master included) which contains commit fdb1a856 from [zanata-platform](https://github.com/zanata/zanata-platform).
```bash
cd client
mvn install
```
Then in this project (assuming you have docker installed):

```
mvn clean package
docker/build.sh
docker run --rm -p 8080:8080 -p 50000:50000 --name zjen zjenkins/dev 
```

### How to use it in pipeline

#### Use standard push and pull
```groovy
node {
    // define common variables
    def gitRepo = 'github.com/huangp/test-repo.git'
    def gitBranch = 'trans'
    def zanataCredentialsId = 'zanata'
    def gitCredentialsId = 'huangp_github'
    
    
    git([url: "https://$gitRepo", branch: gitBranch])
    
    // generated from Pipeline Syntax using general step
    step([$class: 'ZanataBuilder', pullFromZanata: true, pushToZanata: true, zanataCredentialsId: zanataCredentialsId, zanataProjectConfigs: '', zanataLocaleIds: ''])
    
    // copy from https://github.com/jenkinsci/pipeline-examples/blob/master/pipeline-examples/push-git-repo/pushGitRepo.Groovy
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: gitCredentialsId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) {
        sh('git push https://${GIT_USERNAME}:${GIT_PASSWORD}@' + ' ' + gitRepo)
    }
}

```

#### Install tool and run in shell 
Assuming a Zanata CLI version 4.0.0 is pre-configured (it will generate a tool name 'zanata_cli_4_0_0').
```groovy
node {
    // from Pipeline Syntax: select 'tool: Use a tool from a predefined Tool Installation' and then generate script
    tool name: 'zanata_cli_4_0_0', type: 'org.zanata.zanatareposync.ZanataCLIInstall'
    
    withEnv(["CLI_HOME=${ tool 'zanata_cli_4_0_0' }"]) {
         sh '$CLI_HOME/bin/zanata-cli help'
    }
    
}

```

### TODO

- need a special version of zanata client due to http://stackoverflow.com/questions/41253028/how-to-make-jenkins-plugin-aware-of-spi

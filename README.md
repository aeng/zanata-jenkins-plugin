### A Jenkins plugin that helps to synchronize between SCM repository and Zanata
 
[Zanata](https://zanata.org) is an open source translation management platform. 

Once you install this jenkins plugin, you can set up a job to perform typical localization workflow:
- check out from SCM
- push source (and/or translation) to Zanata server
- pull translation from Zanata server
- commit translation into SCM (currently only git is supported automatically. Other SCM will need to use scripting)
- push the commit back to remote SCM (using git publisher or equivalent)

### How to build and test locally
need to install a specific version of zanata client before Zanata release 4.0.1. 
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

### How to use it as normal Jenkins job
First you will need to configure Zanata credentials in Jenkins Credentials view (plus e.g. github credential if you want to push commit).
Then you will have two options to use Zanata client to push source to and/or pull translation from Zanata server.
1. install Zanata CLI on Jenkins node and use scripting to invoke it
    - go to Jenkins global tools configuration view and there will be a Zanata CLI section for you to configure
    - in your job configuration, you can choose to install the configured CLI under Build Environment section
    - choose a shell builder step and run Zanata CLI from there
2. use the plugin as a build step
    - in you job configuration, you can choose 'Zanata Sync' as a build step and fill in all the information in the UI
    
Option 1 has the advantage of being flexible. You can use all the features and options [Zanata CLI](http://zanata-client.readthedocs.io/en/release/).
The disadvantage is, you will need to know how to use Zanata CLI. You are also are tied to shell scripting and can not run it on Window node.
You also need to manually manage commit in your shell script.

Option 2 has the advantage of being installation free and simple to use. It will work on all type of nodes.
It will commit translation after pull automatically if you use Git as SCM. 
Disadvantage being the java classes it used is from a fixed version of Zanata CLI and you can't do much customization for push and pull.

### How to use it in pipeline build

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
    tool name: 'zanata_cli_4_0_0', type: 'org.jenkinsci.plugins.zanata.zanatareposync.ZanataCLIInstall'
    
    withEnv(["CLI_HOME=${ tool 'zanata_cli_4_0_0' }"]) {
         sh '$CLI_HOME/bin/zanata-cli help'
    }
    
}

```

### TODO

- need a special version of zanata client due to http://stackoverflow.com/questions/41253028/how-to-make-jenkins-plugin-aware-of-spi

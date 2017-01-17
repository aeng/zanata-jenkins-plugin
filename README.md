### How to run
need to install a specific version of zanata client. 
Check out git commit fdb1a856 from zanata-platform.
```
cd client
mvn install
```
Then in this project:

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
    
    step([$class: 'ZanataBuilder', pullFromZanata: true, pushToZanata: true, zanataCredentialsId: zanataCredentialsId, zanataProjectConfigs: '', zanataLocaleIds: ''])
    
    // copy from https://github.com/jenkinsci/pipeline-examples/blob/master/pipeline-examples/push-git-repo/pushGitRepo.Groovy
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: gitCredentialsId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) {
        sh('git push https://${GIT_USERNAME}:${GIT_PASSWORD}@' + ' ' + gitRepo)
    }
}

```

#### Install tool and run in shell 
Assuming a Zanata CLI version 4.0.0 is pre-configured (it will generate a tool name 'Zanata_cli_4_0_0').
```groovy
node {
    // from Pipeline Syntax: select 'tool: Use a tool from a predefined Tool Installation' and then generate script
    tool name: 'Zanana_cli_4_0_0', type: 'org.zanata.zanatareposync.ZanataCLIInstall'
    
    withEnv(["CLI_HOME=${ tool 'Zanana_cli_4_0_0' }"]) {
         sh '$CLI_HOME/bin/zanata-cli help'
    }
    
}

```

### TODO

- need a special version of zanata client due to [http://stackoverflow.com/questions/41253028/how-to-make-jenkins-plugin-aware-of-spi](http://stackoverflow.com/questions/41253028/how-to-make-jenkins-plugin-aware-of-spi)
- ~~zanata client is not logging output to jenkins server~~
- ~~zanata push and pull are both async and running both at the same time the result may be undefined~~

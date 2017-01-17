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

### TODO

- need a special version of zanata client due to [http://stackoverflow.com/questions/41253028/how-to-make-jenkins-plugin-aware-of-spi](http://stackoverflow.com/questions/41253028/how-to-make-jenkins-plugin-aware-of-spi)
- ~~zanata client is not logging output to jenkins server~~
- ~~zanata push and pull are both async and running both at the same time the result may be undefined~~

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

### TODO

- need a special version of zanata client due to [http://stackoverflow.com/questions/41253028/how-to-make-jenkins-plugin-aware-of-spi](http://stackoverflow.com/questions/41253028/how-to-make-jenkins-plugin-aware-of-spi)
- zanata client is not logging output to jenkins server
- zanata push and pull are both async and running both at the same time the result may be undefined

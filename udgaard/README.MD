## Run locally

Prerequisites:
* Docker
* Java JVM (21^)
* Node

### Run backend
1. `cd udgaard`
2. Run docker compose using the supplied compose.yaml `docker compose up -d`
3. Create a file `secure.properties` and add it to directory `udgaard\src\main\resources`
4. Add the following properties (check network on ovtlyr website for values):
   5. ovtlyr.cookie.token=XXX
      ovtlyr.cookie.userid=XXX
      ovtlyr.stockinformation.token=XXX
      ovtlyr.stockinformation.userid=XXX 
6. Build backend `./gradlew build`
7. Run backend `java -jar build/libs/udgaard-0.0.1-SNAPSHOT.jar`

### Run UI
1. Build ui `npm i`
2. Run ui `npm run dev`

## Libs

https://fastcsv.org/guides/quickstart/
https://apexcharts.com/




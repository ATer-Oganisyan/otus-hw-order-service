FROM alpine:3.14
EXPOSE 8000
ARG HOST
ARG PORT
ARG USER
ARG PASSWRORD
ARG DB
WORKDIR /www
RUN apk update
RUN apk add openjdk11
RUN apk add git && git clone https://github.com/ATer-Oganisyan/otus-hw-order-service.git && cd otus-hw-order-service && jar xf mysql.jar && javac OrderService.java && apk del git && rm OrderService.java
ENTRYPOINT java -classpath /www/otus-hw-order-service OrderService $HOST $PORT $USER $PASSWRORD $DB v1

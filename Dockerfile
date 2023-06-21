FROM alpine:3.14
EXPOSE 8000
ARG HOST
ARG PORT
ARG USER
ARG PASSWRORD
ARG DB
ARG PAYMENT_HOST
ARG DELIVERY_HOST
ARG SESSION_HOST
ARG STOCK_HOST
WORKDIR /www
RUN apk update
RUN apk add openjdk11
RUN apk add git && git clone https://github.com/ATer-Oganisyan/otus-hw-order-service.git && cd otus-hw-order-service && jar xf mysql.jar && javac OrderService.java && apk del git && rm OrderService.java
ENTRYPOINT java -classpath /www/otus-hw-order-service OrderService $HOST $PORT $USER $PASSWRORD $DB $PAYMENT_HOST $DELIVERY_HOST $SESSION_HOST $STOCK_HOST v120
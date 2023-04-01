Installation manual

Run:

alias k=kubectl
git clone https://github.com/ATer-Oganisyan/otus-hw-order-service.git
cd ./otus-hw-order-service
helm repo add nginx-stable https://helm.nginx.com/stable
helm repo add myZql https://charts.bitnami.com/bitnami
helm repo update
helm install my-release nginx-stable/nginx-ingress		
helm install myzql-release myZql/mysql -f kuber/mysql/values.yml
k apply -f ./kuber/config/
k apply -f ./kuber/mysql/migrations/  
k apply -f ./kuber/app

Import OrderServiceCollection.postman_collection.json into Postman.

Enjoy :)




To build app container run:

docker build -t arsenteroganisyan/order-service:v20 /Users/arsen/otus-hw-order-service --no-cache --platform linux/amd64




To build DB migration container:
 
docker build -t arsenteroganisyan/otus-order-service-sql-migrator:v8 /Users/arsen/otus-hw-order-service/kuber/mysql/migrations --no-cache --platform linux/amd64

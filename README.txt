Installation manual

Run:

git clone https://github.com/ATer-Oganisyan/otushomework.git
cd crud 
alias k=kubectl
helm repo add nginx-stable https://helm.nginx.com/stable
helm repo add myZql https://charts.bitnami.com/bitnami
helm repo update
helm install my-release nginx-stable/nginx-ingress		
helm install myzql-release myZql/mysql -f kuber/mysql/values.yml
k apply -f ./kuber/config/
k apply -f ./kuber/mysql/migrations/  
k apply -f ./kuber

Import Simple_CRUD.postman_collection.json into Postman.

Enjoy :)


docker build -t arsenteroganisyan/otus-order-service-sql-migrator:v1 /Users/arsen/otus-hw-order-service/kuber/mysql/migrations --no-cache --platform linux/amd64
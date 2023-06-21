import java.io.IOException;
import java.io.OutputStream;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.sql.*;
import java.net.http.HttpClient;

public class OrderService {

    static Connection connection = null;
    private static HttpClient client = HttpClient.newBuilder().build();
    private static String sessionServiceHost = "";
    private static String deleveryHost = "";
    private static String paymentHost = "";
    private static String stockHost = "";
    private static String scheme = "http://";

    private static int ORDER_STATUS_CREATED = 1;
    private static int ORDER_STATUS_COMMITED = 2;
    private static int ORDER_STATUS_COMPLETED = 3;
    private static int ORDER_STATUS_REFUNDED = 4;

    private static int PAYMENT_STATUS_WAITING = 1;
    private static int PAYMENT_STATUS_REQUESTED = 2;
    private static int PAYMENT_STATUS_EXECUTED = 3;
    private static int PAYMENT_STATUS_REFUND_REQUESTED = 4;
    private static int PAYMENT_STATUS_REFUNDED = 5;

    private static int DELEVERY_STATUS_NOT_RECEIVED = 1;
    private static int DELEVERY_STATUS_RECEIVED = 2;

    private static int TRANSFER_STATUS_EXECUTED = 2;
    private static int TRANSFER_STATUS_REFUND_EXECUTED = 4;

    public static void main(String[] args) throws Exception {
        String dbHost = args[0];
        String dbPort = args[1];
        String dbUser = args[2];
        String dbPassword = args[3];
        String db = args[4];
        sessionServiceHost = args[7];
        deleveryHost = args[6];
        paymentHost = args[5];
        stockHost = args[8];
        System.out.println("Hardcoded version: v109");
        System.out.println("Version from config:" + args[9]);
        System.out.println(dbHost);
        System.out.println(dbPort);
        System.out.println(dbUser);

        System.out.println("sessionServiceHost: " + sessionServiceHost);
        System.out.println("deleveryHost: " + deleveryHost);
        System.out.println("stockHost: " + stockHost);
        System.out.println("paymentHost: " + paymentHost);

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(null); // creates a default executor
	    Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection("jdbc:mysql://" + dbHost + ":" + dbPort + "/" + db, dbUser, dbPassword);
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("Request accepted");
            String path = t.getRequestURI().getPath();
            System.out.println("Path: " + path);
            if ("/health".equals(path)) {
                routeHealth(t);
                System.out.println("matched");
            } else if ("/orders".equals(path)) { // only admin
                routeOrders(t);
                System.out.println("matched");
            } else if ("/order".equals(path)) { // order.user_id == current user_id || user is admin
                routeOrder(t);
                System.out.println("matched");
            } else if ("/order/create".equals(path)) {
                routeCreateOrder(t);
                System.out.println("matched");
            } else if ("/order/add-item".equals(path)) {
                routeAddItem(t);
                System.out.println("matched");
            } else if ("/order/commit".equals(path)) { // should be checking for counts and stocks
                routeCommitOrder(t);
                System.out.println("matched");
            } else if ("/order/complete".equals(path)) { // check payment and delivery statuses and set order status
                routeCompleteOrder(t);
                System.out.println("matched");
            } else if ("/order/set-delivery-slot".equals(path)) { // admin or current user
                routeSetDeliverySlot(t);
                System.out.println("matched");
            } else if ("/order/set-delivered".equals(path)) { // only admin can do it, and status will be checked
                routeSetDelivered(t);
                System.out.println("matched");
            } else if ("/order/request-purchase".equals(path)) { //
                routeRequestPurchase(t);
                System.out.println("matched");
            } else if ("/order/purchase-result".equals(path)) { //status will be checked
                routePurchaseResult(t);
                System.out.println("matched");
            } else if ("/order/request-refund".equals(path)) { // only admin can do it
                routeRequestRefund(t);
                System.out.println("matched");
            } else if ("/order/result-refund".equals(path)) { // status will be checked
                routeRefundResult(t);
                System.out.println("matched");
            } else {
                String response = "{\"status\": \"not found\"}";
                t.sendResponseHeaders(404, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                System.out.println("not matched");
            }
        }
    }

    static private void routeHealth(HttpExchange t) throws IOException {
        System.out.println("Request accepted");
        String response = "{\"status\": \"OK\"}";
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    static private void routeOrders(HttpExchange t) throws IOException {
        System.out.println("Read request accepted");
        Map<String, String> userInfo = getUserInfo(t);

        if (!"admin".equals(userInfo.get("role"))) {
            System.out.println("error:404 only admin can get all orders");
            String r = "not permitted";
            t.sendResponseHeaders(403, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
            return;
        }

        String r;
        try {
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery("select id, request_id, created_at, user_id, slot_id, status_id, payment_status_id, delivery_status_id from orders");
            List<String> items = new ArrayList<>();
            while (rs.next()) {
                String id = "" + rs.getInt(1);
                String request_id = rs.getString(2);
                String created_at = "" + rs.getTimestamp(3).toString();
                String userId = rs.getString(4);
                String slotId = "" + rs.getInt(5);
                String status = getStatusById(rs.getInt(6));
                String paymentStatus = getPaymentStatusById(rs.getInt(7));
                String deliveryStatus = getDeliveryStatusById(rs.getInt(8));
                r = "{id: " + id + ", request_id: " + request_id + ", user_id: " + userId + ", slot_id: " + slotId + ", status: " + status +  ", payment_status:" + paymentStatus + ", delivery_status:" + deliveryStatus + "}";
                items.add(r);
            }
            r = "{" + String.join(",", items) + "}";
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            System.out.println("success");
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
        }
        OutputStream os = t.getResponseBody();
        os.write(r.getBytes());
        os.close();
    }

    static private Map<String, Map<String, String>> getCatalogInfo(HttpExchange t) {
        // GET TOKEN FROM COOKIE
        Headers headers = t.getRequestHeaders();
        System.out.println("headers = " + headers);
        List<String> headersList;
        if (headers == null) {
            System.out.println("headers = null");
            headersList = new ArrayList<>();
        } else {
            System.out.println("headers.get");
            headersList = headers.get("Cookie");
        }
        String cookieString = String.join(";", headersList);
        System.out.println("cookieString = " + cookieString);
        Map<String, String> cookie = postToMap(new StringBuilder(cookieString));
        System.out.println("cookie = " + cookie);
        String token = cookie.get("token");
        System.out.println("token = " + token);


        // CALL SESSION SERVICE TO GET USER_ID
        String r;
        String body = "token:" + token;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(scheme + stockHost + "/catalog"))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "plain/text")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response;
        System.out.println("HttpResponse<String> response;");
        try {
            System.out.println("try");
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("response = client.send(request, BodyHandlers.ofString());");
        } catch (IOException e) {
            System.out.println("IOException");
            throw new RuntimeException();
        } catch (InterruptedException e) {
            System.out.println("InterruptedException");
            throw new RuntimeException();
        }

        System.out.println("Map<String, String> userInfo = postToMap(new StringBuilder(response.body()));");
        return parseCatalog(new StringBuilder(response.body()));
    }

    static private Map<String, String> getUserInfo(HttpExchange t) {
            // GET TOKEN FROM COOKIE
            Headers headers = t.getRequestHeaders();
            List<String> headersList;
            if (headers == null) {
                System.out.println("headers = null");
                headersList = new ArrayList<>();
            } else {
                System.out.println("headers.get");
                headersList = headers.get("Cookie");
            }
            String cookieString = String.join(";", headersList);
            System.out.println("cookieString = " + cookieString);
            Map<String, String> cookie = postToMap(new StringBuilder(cookieString));
            System.out.println("cookie = " + cookie);
            String token = cookie.get("token");
            System.out.println("token = " + token);


            // CALL SESSION SERVICE TO GET USER_ID
            String r;
            String body = "token:" + token;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(scheme + sessionServiceHost + "/session"))
                    .timeout(Duration.ofMinutes(1))
                    .header("Content-Type", "plain/text")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response;
            System.out.println("HttpResponse<String> response;");
            try {
                System.out.println("try");
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("response = client.send(request, BodyHandlers.ofString());");
            } catch (IOException e) {
                System.out.println("IOException");
                throw new RuntimeException();
            } catch (InterruptedException e) {
                System.out.println("InterruptedException");
                throw new RuntimeException();
            }

            System.out.println("Map<String, String> userInfo = postToMap(new StringBuilder(response.body()));");
            return postToMap(new StringBuilder(response.body()));
        }

    static private boolean accuquireItem(String orderId, String catalogId, String cnt, HttpExchange t) {
        Headers headers = t.getRequestHeaders();
        String requestId = "req" + orderId + catalogId + cnt;
        System.out.println("headers = " + headers);
        List<String> headersList;
        if (headers == null) {
            System.out.println("headers = null");
            headersList = new ArrayList<>();
        } else {
            System.out.println("headers.get");
            headersList = headers.get("Cookie");
        }
        String cookieString = String.join(";", headersList);
        System.out.println("cookieString = " + cookieString);
        Map<String, String> cookie = postToMap(new StringBuilder(cookieString));
        System.out.println("cookie = " + cookie);
        String token = cookie.get("token");
        String body = "catalog_id:" + catalogId + "\norder_Id:" + orderId + "\ncount:" + cnt + "\nrequest_id:" + requestId;
        System.out.println("http request to stock service: " + body);

        String r;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(scheme + stockHost + "/accquire-item"))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "plain/text")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response;
        System.out.println("HttpResponse<String> response;");
        try {
            System.out.println("try");
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("response = client.send(request, BodyHandlers.ofString());");
            return response.statusCode() == 200 ? true : false;
        } catch (IOException e) {
            System.out.println("IOException");
            return false;
        } catch (InterruptedException e) {
            System.out.println("InterruptedException");
            return false;
        }
    }

    static private int transferStatus(String orderId) {
            String body = "order_id:" + orderId;
            System.out.println("http request to payment service: " + body);
            String r;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(scheme + paymentHost + "/transfer/status"))
                    .timeout(Duration.ofMinutes(1))
                    .header("Content-Type", "plain/text")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response;
            System.out.println("HttpResponse<String> response;");
            try {
                System.out.println("try");
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("response = client.send(request, BodyHandlers.ofString());");
                Map<String, String> respMap = postToMap(new StringBuilder(response.body()));
                return Integer.valueOf(respMap.get("status_id"));
            } catch (IOException e) {
                System.out.println("IOException");
                return -1;
            } catch (InterruptedException e) {
                System.out.println("InterruptedException");
                return -1;
            }
        }

    static private boolean transferRefund(String orderId) {
        String body = "order_id:" + orderId;
        System.out.println("http request to payment service: " + body);
        String r;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(scheme + paymentHost + "/transfer/refund"))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "plain/text")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response;
        System.out.println("HttpResponse<String> response;");
        try {
            System.out.println("try");
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("response = client.send(request, BodyHandlers.ofString());");
            return response.statusCode() == 200 ? true : false;
        } catch (IOException e) {
            System.out.println("IOException");
            return false;
        } catch (InterruptedException e) {
            System.out.println("InterruptedException");
            return false;
        }
    }

    static private boolean transferPurchase(String orderId, int amount) {
            String body = "order_id:" + orderId;
            System.out.println("http request to payment service: " + body);
            String r;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(scheme + paymentHost + "/transfer/refund"))
                    .timeout(Duration.ofMinutes(1))
                    .header("Content-Type", "plain/text")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response;
            System.out.println("HttpResponse<String> response;");
            try {
                System.out.println("try");
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("response = client.send(request, BodyHandlers.ofString());");
                return response.statusCode() == 200 ? true : false;
            } catch (IOException e) {
                System.out.println("IOException");
                return false;
            } catch (InterruptedException e) {
                System.out.println("InterruptedException");
                return false;
            }
        }

    static private boolean accquireSlot(String slotId) {
        String body = "slot_id:" + slotId;
        System.out.println("http request to payment service: " + body);
        String r;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(scheme + deleveryHost + "/accquire-slot"))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "plain/text")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response;
        System.out.println("HttpResponse<String> response;");
        try {
            System.out.println("try");
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("response = client.send(request, BodyHandlers.ofString());");
            return response.statusCode() == 200 ? true : false;
        } catch (IOException e) {
            System.out.println("IOException");
            return false;
        } catch (InterruptedException e) {
            System.out.println("InterruptedException");
            return false;
        }
    }

    static private boolean releaseItem(String orderId, String catalogId, String cnt, HttpExchange t) {
            Headers headers = t.getRequestHeaders();
            String requestId = "req" + orderId + catalogId + cnt;
            System.out.println("headers = " + headers);
            List<String> headersList;
            if (headers == null) {
                System.out.println("headers = null");
                headersList = new ArrayList<>();
            } else {
                System.out.println("headers.get");
                headersList = headers.get("Cookie");
            }
            String cookieString = String.join(";", headersList);
            System.out.println("cookieString = " + cookieString);
            Map<String, String> cookie = postToMap(new StringBuilder(cookieString));
            System.out.println("cookie = " + cookie);
            String token = cookie.get("token");
            String body = "catalog_id:" + catalogId + "\norder_Id:" + orderId + "\ncount:" + cnt + "\nrequest_id:" + requestId;
            System.out.println("http request to stock service: " + body);

            String r;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(scheme + stockHost + "/release-item"))
                    .timeout(Duration.ofMinutes(1))
                    .header("Content-Type", "plain/text")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response;
            System.out.println("HttpResponse<String> response;");
            try {
                System.out.println("try");
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("response = client.send(request, BodyHandlers.ofString());");
                return response.statusCode() == 200 ? true : false;
            } catch (IOException e) {
                System.out.println("IOException");
                return false;
            } catch (InterruptedException e) {
                System.out.println("InterruptedException");
                return false;
            }
        }

    static private String getStatusById(int status) {
        if (status == 2) return "In progress";
        if (status == 3) return "Done";
        return "Created";
    }

    static private String getPaymentStatusById(int status) {
        if (status == 1) return "Waiting";
        if (status == 2) return "Requested";
        if (status == 3) return "Executed";
        if (status == 4) return "Refund requested";
        if (status == 5) return "Refund executed";
        return "Unknown";
    }

    static private String getDeliveryStatusById(int status) {
        if (status == 1) return "Not recieved";
        return "Received";
    }

    static private void routeOrder(HttpExchange t) throws IOException {
        System.out.println("Read request accepted");
        Map<String, String> q = queryToMap(t.getRequestURI().getQuery());
        Map<String, String> userInfo = getUserInfo(t);
        Map<String, Map<String, String>> catalogInfo = getCatalogInfo(t);
        String qId = q.get("id");
        String r;
        String id = "";
        String request_id = "";
        String userId = "";
        String created_at = "";
        String cnt = "";
        String status = "";
        String catalogId = "";

        try {
            Statement stmt=connection.createStatement();
            String orderSql = "select id, request_id, created_at, user_id, slot_id, status_id, payment_status_id, delivery_status_id  from orders o where o.id = " + qId;
            System.out.println("sql: " + orderSql);
            ResultSet rs=stmt.executeQuery(orderSql);

            List<String> items = new ArrayList<>();
            if (!rs.next()) {
                r = "order is not found";
                t.sendResponseHeaders(404, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write("order is not found".getBytes());
                os.close();
                return;
            }

            id = "" + rs.getInt(1);
            request_id = rs.getString(2);
            created_at = "" + rs.getTimestamp(3).toString();
            userId = rs.getString(4);
            String slotId = "" + rs.getInt(5);
            status = getStatusById(rs.getInt(6));
            String paymentStatus = getPaymentStatusById(rs.getInt(7));
            String deliveryStatus = getDeliveryStatusById(rs.getInt(8));
            if (!userId.equals(userInfo.get("id")) && !"admin".equals(userInfo.get("role"))) {
                r = "not permitted";
                t.sendResponseHeaders(403, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            stmt=connection.createStatement();
            orderSql = "select catalog_id, cnt from order_itmes where order_id = " + qId;
            System.out.println("sql: " + orderSql);
            rs=stmt.executeQuery(orderSql);

            while (rs.next()) {
                cnt = "" + rs.getInt(8);
                String goodCode = catalogInfo.get(id).get("good_code");
                String goodName = catalogInfo.get(id).get("good_name");
                String goodDescription = catalogInfo.get(id).get("good_name");
                String pricePerUnit = catalogInfo.get(id).get("price_per_unit");
                String measurementUnits = catalogInfo.get(id).get("measurementUnits");
                String item = "{good_code:" + goodCode + ",good_name:" + goodName + ",good_description:" + goodDescription + ",count:" + cnt + ",price_per_unit:" + pricePerUnit +  ",measurementUnits:" + measurementUnits + "}";
                items.add(item);
            }

            String itemsJson = "{" + String.join(", \n", items) + "}";

            r = "{id: " + id + ", request_id: " + request_id + ", user_id: " + userId + ", slot_id: " + slotId + ", status: " + status +  ", payment_status:" + paymentStatus + ", delivery_status:" + deliveryStatus + ", " +
                    "items: { " + itemsJson + " }" +
                    "}";

            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            System.out.println("success");
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
        }
        OutputStream os = t.getResponseBody();
        os.write(r.getBytes());
        os.close();
    }

    static private void routeSetDeliverySlot(HttpExchange t) throws IOException {
        System.out.println("routeRequestRefund accepted");
        Map<String, String> q = postToMap(buf(t.getRequestBody()));
        String orderId = q.get("order_id");
        String slotId = q.get("slot_id");
        Map<String, String> userInfo = getUserInfo(t);

        try {

            String orderSql = "select o.id, o.status_id, o.delivery_status_id, o.slot_id, o.user_id from orders o where o.id = " + orderId;
            System.out.println("sql: " + orderSql);
            ResultSet rs=connection.createStatement().executeQuery(orderSql);

            if (!rs.next()) {
                String r = "order is not found";
                t.sendResponseHeaders(404, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            if (!(userInfo.get("role").equals("admin")) && !userInfo.get("id").equals(rs.getInt(5))) {
                String r = "not permitted";
                t.sendResponseHeaders(403, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            if (rs.getInt(3) == DELEVERY_STATUS_RECEIVED || rs.getInt(4) != 0) {
                String r = "incorrect status";
                t.sendResponseHeaders(409, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            if (accquireSlot(slotId)) {
                Statement _stmt2=connection.createStatement();
                String sql = "update orders set slot_id = \"" + slotId + "\" where id = " + orderId;
                _stmt2.executeUpdate(sql);
                String r = "";
                t.sendResponseHeaders(200, r.length());
                System.out.println("ok");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
            } else {
                String r = "cant set slot";
                t.sendResponseHeaders(500, r.length());
                System.out.println("ok");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
            }
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            String r = "internal server error";
            t.sendResponseHeaders(500, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
        }
    }

    static private void routeCommitOrder(HttpExchange t) throws IOException {
        System.out.println("Read request accepted");
        Map<String, String> q = postToMap(buf(t.getRequestBody()));
        String orderId = q.get("order_id");
        Map<String, String> userInfo = getUserInfo(t);

        try {

            String orderSql = "select o.id, o.status_id, o.user_id, o.slot_id  from orders o where o.id = " + orderId;
            System.out.println("sql: " + orderSql);
            ResultSet rs=connection.createStatement().executeQuery(orderSql);

            if (!rs.next()) {
                String r = "order is not found";
                t.sendResponseHeaders(404, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            } else {

                if (!userInfo.get("id").equals(rs.getString(3))) {
                    String r = "not permitted";
                    t.sendResponseHeaders(403, r.length());
                    System.out.println(r);
                    OutputStream os = t.getResponseBody();
                    os.write(r.getBytes());
                    os.close();
                    return;
                }

                if (rs.getInt(2) != ORDER_STATUS_CREATED) {
                    String r = "incorrect order status";
                    t.sendResponseHeaders(409, r.length());
                    System.out.println(r);
                    OutputStream os = t.getResponseBody();
                    os.write(r.getBytes());
                    os.close();
                    return;
                }

                if (rs.getString(4) == null || "".equals(rs.getString(4))) {
                    String r = "delivery slot is not set";
                    t.sendResponseHeaders(409, r.length());
                    System.out.println(r);
                    OutputStream os = t.getResponseBody();
                    os.write(r.getBytes());
                    os.close();
                    return;
                }
            }


            Statement _stmt=connection.createStatement();
            rs=_stmt.executeQuery("select * from order_items where order_id = \"" + orderId + " \"");
            if (!rs.next()) {
                String r = "order is emoty";
                t.sendResponseHeaders(409, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            Statement _stmt2=connection.createStatement();
            int amount = 500; // todo: calculate order amount
            String sql = "update orders set status = " + ORDER_STATUS_COMMITED + ", amount = " + amount + " where id = " + orderId;
            _stmt2.executeUpdate(sql);
            String r = "";
            t.sendResponseHeaders(200, r.length());
            System.out.println("ok");
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
            return;
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            String r = "internal server error";
            t.sendResponseHeaders(500, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
        }
    }

    static private void routeCompleteOrder(HttpExchange t) throws IOException {
            System.out.println("Read request accepted");
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            String orderId = q.get("order_id");
            Map<String, String> userInfo = getUserInfo(t);

            try {

                String orderSql = "select o.id, o.status_id, o.delivery_status_id, o.payment_status_id from orders o where o.id = " + orderId;
                System.out.println("sql: " + orderSql);
                ResultSet rs=connection.createStatement().executeQuery(orderSql);

                if (!rs.next()) {
                    String r = "order is not found";
                    t.sendResponseHeaders(404, r.length());
                    System.out.println(r);
                    OutputStream os = t.getResponseBody();
                    os.write(r.getBytes());
                    os.close();
                    return;
                } else {

                    if (!userInfo.get("id").equals(rs.getString(3))) {
                        String r = "not permitted";
                        t.sendResponseHeaders(403, r.length());
                        System.out.println(r);
                        OutputStream os = t.getResponseBody();
                        os.write(r.getBytes());
                        os.close();
                        return;
                    }

                    if (rs.getInt(2) != ORDER_STATUS_COMMITED) {
                        String r = "incorrect order status";
                        t.sendResponseHeaders(409, r.length());
                        System.out.println(r);
                        OutputStream os = t.getResponseBody();
                        os.write(r.getBytes());
                        os.close();
                        return;
                    }

                    if (rs.getString(4) == null || "".equals(rs.getString(4))) {
                        String r = "delivery slot is not set";
                        t.sendResponseHeaders(409, r.length());
                        System.out.println(r);
                        OutputStream os = t.getResponseBody();
                        os.write(r.getBytes());
                        os.close();
                        return;
                    }

                    if (rs.getInt(3) != PAYMENT_STATUS_EXECUTED) {
                        String r = "payment is not executed";
                        t.sendResponseHeaders(409, r.length());
                        System.out.println(r);
                        OutputStream os = t.getResponseBody();
                        os.write(r.getBytes());
                        os.close();
                        return;
                    }

                    if (rs.getInt(4) != DELEVERY_STATUS_RECEIVED) {
                        String r = "payment is not executed";
                        t.sendResponseHeaders(409, r.length());
                        System.out.println(r);
                        OutputStream os = t.getResponseBody();
                        os.write(r.getBytes());
                        os.close();
                        return;
                    }
                }

                Statement _stmt2=connection.createStatement();
                String sql = "update orders set status = " + ORDER_STATUS_COMPLETED + " where id = " + orderId;
                _stmt2.executeUpdate(sql);
                String r = "";
                t.sendResponseHeaders(200, r.length());
                System.out.println("ok");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            } catch (Throwable e) {
                System.out.println("error: " + e.getMessage());
                String r = "internal server error";
                t.sendResponseHeaders(500, r.length());
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
            }
        }

    static private void routeSetDelivered(HttpExchange t) throws IOException {
            System.out.println("Read request accepted");
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            String orderId = q.get("order_id");
            Map<String, String> userInfo = getUserInfo(t);

            try {

                String orderSql = "select o.id, o.status_id, o.delivery_status_id, o.slot_id from orders o where o.id = " + orderId;
                System.out.println("sql: " + orderSql);
                ResultSet rs=connection.createStatement().executeQuery(orderSql);

                if (!rs.next()) {
                    String r = "order is not found";
                    t.sendResponseHeaders(404, r.length());
                    System.out.println(r);
                    OutputStream os = t.getResponseBody();
                    os.write(r.getBytes());
                    os.close();
                    return;
                } else {

                    if (!userInfo.get("role").equals("admin")) {
                        String r = "not permitted";
                        t.sendResponseHeaders(403, r.length());
                        System.out.println(r);
                        OutputStream os = t.getResponseBody();
                        os.write(r.getBytes());
                        os.close();
                        return;
                    }

                    if (rs.getInt(2) != ORDER_STATUS_COMMITED) {
                        String r = "incorrect order status";
                        t.sendResponseHeaders(409, r.length());
                        System.out.println(r);
                        OutputStream os = t.getResponseBody();
                        os.write(r.getBytes());
                        os.close();
                        return;
                    }

                    if (rs.getInt(3) == DELEVERY_STATUS_NOT_RECEIVED || rs.getString("4") == null || "".equals(rs.getString("4"))) {
                        String r = "delivery slot is not set or incorrect status";
                        t.sendResponseHeaders(409, r.length());
                        System.out.println(r);
                        OutputStream os = t.getResponseBody();
                        os.write(r.getBytes());
                        os.close();
                        return;
                    }
                }

                Statement _stmt2=connection.createStatement();
                String sql = "update orders set delivery_status_id = " + DELEVERY_STATUS_RECEIVED + " where id = " + orderId;
                _stmt2.executeUpdate(sql);
                String r = "";
                t.sendResponseHeaders(200, r.length());
                System.out.println("ok");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            } catch (Throwable e) {
                System.out.println("error: " + e.getMessage());
                String r = "internal server error";
                t.sendResponseHeaders(500, r.length());
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
            }
        }

    static private void routePurchaseResult(HttpExchange t) throws IOException {
        System.out.println("Read request accepted");
        Map<String, String> q = postToMap(buf(t.getRequestBody()));
        String orderId = q.get("order_id");
        Map<String, String> userInfo = getUserInfo(t);

        try {

            String orderSql = "select o.id, o.status_id, o.payment_status_id, o.slot_id, o.user_id from orders o where o.id = " + orderId;
            System.out.println("sql: " + orderSql);
            ResultSet rs=connection.createStatement().executeQuery(orderSql);

            if (!rs.next()) {
                String r = "order is not found";
                t.sendResponseHeaders(404, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            if (rs.getInt(3) != PAYMENT_STATUS_REQUESTED) {
                String r = "incorrect status";
                t.sendResponseHeaders(409, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            int status = transferStatus(orderId);
            if (status != TRANSFER_STATUS_EXECUTED) {
                String r = "incorrect payment status";
                t.sendResponseHeaders(409, r.length());
                System.out.println("ok");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            Statement _stmt2=connection.createStatement();
            String sql = "update orders set payment_status_id = " + PAYMENT_STATUS_EXECUTED + " where id = " + orderId;
            _stmt2.executeUpdate(sql);
            String r = "";
            t.sendResponseHeaders(200, r.length());
            System.out.println("ok");
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
            return;
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            String r = "internal server error";
            t.sendResponseHeaders(500, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
        }
    }

    static private void routeRefundResult(HttpExchange t) throws IOException {
            System.out.println("Read request accepted");
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            String orderId = q.get("order_id");
            Map<String, String> userInfo = getUserInfo(t);

            try {

                String orderSql = "select o.id, o.status_id, o.delivery_status_id, o.slot_id, o.user_id from orders o where o.id = " + orderId;
                System.out.println("sql: " + orderSql);
                ResultSet rs=connection.createStatement().executeQuery(orderSql);

                if (!rs.next()) {
                    String r = "order is not found";
                    t.sendResponseHeaders(404, r.length());
                    System.out.println(r);
                    OutputStream os = t.getResponseBody();
                    os.write(r.getBytes());
                    os.close();
                    return;
                }

                if (rs.getInt(3) != PAYMENT_STATUS_REFUND_REQUESTED) {
                    String r = "incorrect status";
                    t.sendResponseHeaders(409, r.length());
                    System.out.println(r);
                    OutputStream os = t.getResponseBody();
                    os.write(r.getBytes());
                    os.close();
                    return;
                }

                int status = transferStatus(orderId);
                if (status != TRANSFER_STATUS_REFUND_EXECUTED) {
                    String r = "incorrect payment status";
                    t.sendResponseHeaders(409, r.length());
                    System.out.println("ok");
                    OutputStream os = t.getResponseBody();
                    os.write(r.getBytes());
                    os.close();
                    return;
                }

                Statement _stmt2=connection.createStatement();
                String sql = "update orders set payment_status_id = " + PAYMENT_STATUS_REFUNDED + " where id = " + orderId;
                _stmt2.executeUpdate(sql);
                String r = "";
                t.sendResponseHeaders(200, r.length());
                System.out.println("ok");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            } catch (Throwable e) {
                System.out.println("error: " + e.getMessage());
                String r = "internal server error";
                t.sendResponseHeaders(500, r.length());
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
            }
        }

    static private void routeRequestRefund(HttpExchange t) throws IOException {
        System.out.println("routeRequestRefund accepted");
        Map<String, String> q = postToMap(buf(t.getRequestBody()));
        String orderId = q.get("order_id");
        Map<String, String> userInfo = getUserInfo(t);

        try {

            String orderSql = "select o.id, o.status_id, o.payment_status_id, o.slot_id, o.user_id from orders o where o.id = " + orderId;
            System.out.println("sql: " + orderSql);
            ResultSet rs=connection.createStatement().executeQuery(orderSql);

            if (!rs.next()) {
                String r = "order is not found";
                t.sendResponseHeaders(404, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            if (!userInfo.get("role").equals("admin")) {
                String r = "not permitted";
                t.sendResponseHeaders(403, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            if (rs.getInt(3) != PAYMENT_STATUS_EXECUTED) {
                String r = "incorrect status";
                t.sendResponseHeaders(409, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            if (transferRefund(orderId)) {
                Statement _stmt2=connection.createStatement();
                String sql = "update orders set payment_status_id = " + PAYMENT_STATUS_REFUNDED + " where id = " + orderId;
                _stmt2.executeUpdate(sql);
                String r = "";
                t.sendResponseHeaders(200, r.length());
                System.out.println("ok");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
            } else {
                String r = "cant create refund";
                t.sendResponseHeaders(500, r.length());
                System.out.println("ok");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
            }
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            String r = "internal server error";
            t.sendResponseHeaders(500, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
        }
    }

    static private void routeRequestPurchase(HttpExchange t) throws IOException {
        System.out.println("routeRequestRefund accepted");
        Map<String, String> q = postToMap(buf(t.getRequestBody()));
        String orderId = q.get("order_id");
        Map<String, String> userInfo = getUserInfo(t);

        try {

            String orderSql = "select o.id, o.status_id, o.payment_status_id, o.slot_id, o.user_id, o.amount from orders o where o.id = " + orderId;
            System.out.println("sql: " + orderSql);
            ResultSet rs=connection.createStatement().executeQuery(orderSql);

            if (!rs.next()) {
                String r = "order is not found";
                t.sendResponseHeaders(404, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            if (!userInfo.get("id").equals(rs.getString("5"))) {
                String r = "not permitted";
                t.sendResponseHeaders(403, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            if (rs.getInt(3) != PAYMENT_STATUS_WAITING) {
                String r = "incorrect status";
                t.sendResponseHeaders(409, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            int amount = rs.getInt(6);
            if (transferPurchase(orderId, amount)) {
                Statement _stmt2=connection.createStatement();
                String sql = "update orders set payment_status_id = " + PAYMENT_STATUS_REQUESTED + " where id = " + orderId;
                _stmt2.executeUpdate(sql);
                String r = "";
                t.sendResponseHeaders(200, r.length());
                System.out.println("ok");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
            } else {
                String r = "cant create transfer";
                t.sendResponseHeaders(500, r.length());
                System.out.println("ok");
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
            }
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            String r = "internal server error";
            t.sendResponseHeaders(500, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
        }
    }

    static private void routeAddItem(HttpExchange t) throws IOException {
        System.out.println("routeAddItem accepted");
        Map<String, String> q = queryToMap(t.getRequestURI().getQuery());
        Map<String, String> userInfo = getUserInfo(t);
        Map<String, Map<String, String>> catalogInfo = getCatalogInfo(t);
        String orderId = q.get("order_id");
        String catalogId = q.get("catalog_id");
        String reqId = q.get("request_id");
        String cnt = q.get("count");
        try {
            Statement stmt=connection.createStatement();
            String orderSql = "select o.id, o.user_id, o.status_id, o.created_at  from orders o where o.id = " + orderId;
            System.out.println("sql: " + orderSql);
            ResultSet rs=stmt.executeQuery(orderSql);

            if (rs.next()) {
                String id = "" + rs.getInt(1);
                String request_id = rs.getString(2);
                String userId = "" + rs.getString(3);
                int status = rs.getInt(4);

                if (!userId.equals(userInfo.get("id")) || !"amdin".equals(userInfo.get("role"))) {
                    String r = "not permitted";
                    t.sendResponseHeaders(403, r.length());
                    System.out.println(r);
                    OutputStream os = t.getResponseBody();
                    os.write(r.getBytes());
                    os.close();
                    return;
                }

                if (status != ORDER_STATUS_CREATED) {
                    String r = "incorrect order status";
                    t.sendResponseHeaders(409, r.length());
                    System.out.println(r);
                    OutputStream os = t.getResponseBody();
                    os.write(r.getBytes());
                    os.close();
                    return;
                }
            }

            stmt=connection.createStatement();
            orderSql = "select * from order_items where request_id = \"" + reqId + "\"";
            System.out.println("sql: " + orderSql);
            rs=stmt.executeQuery(orderSql);

            if (rs.next()) {
                String r = "dublicate request";
                t.sendResponseHeaders(409, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            Statement _stmt2=connection.createStatement();
            String sql = "insert into order_items (request_id, order_id, catalog_id, cnt) values (\"" + reqId + "\", \"" + orderId + "\", \"" + catalogId + "\", " + cnt + ")";

            if (accuquireItem(orderId, catalogId, cnt, t)) {
                try {
                    _stmt2.executeUpdate(sql);
                } catch (SQLException e) {
                    releaseItem(orderId, catalogId, cnt, t);
                    throw e;
                }
            } else {
                String r = "couldn't add item";
                t.sendResponseHeaders(409, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            System.out.println("send headers");
            t.sendResponseHeaders(200, "".length());
            System.out.println("success");
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            String r = "internal server error";
            t.sendResponseHeaders(500, r.length());
        }
    }

    static private Map<String, String> queryToMap(String query) {
        if(query == null) {
            return new HashMap<>();
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            }else{
                result.put(entry[0], "");
            }
        }
        return result;
    }

    static private Map<String, String> postToMap(StringBuilder body){
        String[] parts = body
                .toString()
                .replaceAll("=", ":")
                .replaceAll("\r", "")
                .replaceAll(" ", "")
                .replaceAll(";", "\n")
                .split("\n");
        Map<String, String> result = new HashMap<>();
        for (String part: parts) {
            String[] keyVal = part.split(":");
            result.put(keyVal[0], keyVal[1]);
        }
        System.out.println("postToMap: " + result.toString());
        return result;
    }

    static private List<List<String>> postToList(StringBuilder body){
        String[] parts = body
                .toString()
                .replaceAll("\r", "")
                .split("\n");
        List<List<String>> result = new ArrayList<>();
        for (String part: parts) {
            String[] keyVal = part.split(":");
            List<String> l = new ArrayList<>();
            l.add(0, keyVal[0]);
            l.add(1, keyVal[1]);
            result.add(l);
        }
        System.out.println("postToList: " + result.toString());
        return result;
    }

    static private Map<String, Map<String, String>> parseCatalog(StringBuilder body){
        Map<String, Map<String, String>> result = new HashMap<>();
        String[] rows = body
                .toString()
                .replaceAll("\r", "")
                .split("\n");

        for (String row: rows) {
            String[] parts = row.split(",");
            Map<String, String> rowMap = new HashMap<>();
            for (String part: parts) {
                String[] keyVal = part.split(":");
                rowMap.put(keyVal[0], keyVal[1]);
            }
            result.put(rowMap.get("id"), rowMap);
        }
        System.out.println("parseCatalogResult: " + result.toString());
        return result;
    }

    static private StringBuilder buf(InputStream inp)  throws UnsupportedEncodingException, IOException {
        InputStreamReader isr =  new InputStreamReader(inp,"utf-8");
        BufferedReader br = new BufferedReader(isr);
        int b;
        StringBuilder buf = new StringBuilder(512);
        while ((b = br.read()) != -1) {
            buf.append((char) b);
        }
        br.close();
        isr.close();
        System.out.println("buf : " + buf);
        return buf;
    }

    static private void routeCreateOrder(HttpExchange t) throws IOException {
        System.out.println("Read request accepted");
        Map<String, String> q = postToMap(buf(t.getRequestBody()));
        String  request_id = q.get("request_id");

        String user = getUserInfo(t).get("id");
        if ("".equals(user) || user == null) {
            String r = "not permitted";
            t.sendResponseHeaders(403, r.length());
            System.out.println(r);
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
            return;
        }


        try {
            Statement _stmt=connection.createStatement();
            ResultSet rs=_stmt.executeQuery("select * from orders where request_id = \"" + request_id + " \"");
            if (rs.next()) {
                String r = "order_allready_exists";
                t.sendResponseHeaders(200, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            Statement _stmt2=connection.createStatement();
            String sql = "insert into orders (request_id, payment_amount, user_id) values (\"" + request_id + "\", null, \"" + user + "\")";

            _stmt2.executeUpdate(sql);
            System.out.println("request to database: " + sql);
            String r = "";
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            System.out.println("success");
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            String r = "internal server error";
            t.sendResponseHeaders(500, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
        }
    }
}
import java.io.IOException;
import java.io.OutputStream;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.sql.*;

public class OrderService {

    static Connection connection = null;

    public static void main(String[] args) throws Exception {
        String host = args[0];
        String port = args[1];
        String user = args[2];
        String password = args[3];
        String db = args[4];
        System.out.println("Started: v5");
        System.out.println(host);
        System.out.println(port);
        System.out.println(user);
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(null); // creates a default executor
	    Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection("jdbc:mysql://" + host + ":"+port + "/"+ db, user, password);
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
            } else if ("/orders".equals(path)) {
                routeOrders(t);
                System.out.println("matched");
            } else if ("/order".equals(path)) {
                routeOrder(t);
                System.out.println("matched");
            } else if ("/order/create".equals(path)) {
                routeCreateOrder(t);
                System.out.println("matched");
            } else if ("/goods/create".equals(path)) {
                routeCreateGood(t);
                System.out.println("matched");
            }
            else if ("/goods".equals(path)) {
                routeGoods(t);
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
        String r;
        try {
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery("select id, request_id, created_at, client_name, client_contact, status_id from orders");
            List<String> items = new ArrayList<>();
            while (rs.next()) {
                String id = "" + rs.getInt(0);
                String request_id = rs.getString(1);
                String created_at = "" + rs.getInt(2);
                String client_name = rs.getString(3);
                String client_contact = "" + rs.getInt(4);
                String status = getStatusById(rs.getInt(5));
                r = "{id: " + id + ", request_id: " + request_id + ", created_at: " + created_at + ", client_name: " + client_name + ", client_contact: " + client_contact + ", status: " + status +  " }";
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

    static private String getStatusById(int status) {
        if (status == 2) return "In progress";
        if (status == 3) return "Done";
        return "Created";
    }

    static private void routeGoods(HttpExchange t) throws IOException {
        System.out.println("Read request accepted");
        String r;
        try {
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery("select id, good_code, good_name, good_description, measurement_units, price_per_unit from catalog");
            List<String> items = new ArrayList<>();
            while (rs.next()) {
                String id = "" + rs.getInt(0);
                String good_code = rs.getString(1);
                String good_name = "" + rs.getInt(2);
                String good_description = rs.getString(3);
                String measurement_units = "" + rs.getInt(4);
                String price_per_unit = "" + rs.getInt(5);
                r = "{id: " + id + ", good_code: " + good_code + ", good_name: " + good_name + ", good_description: " + good_description + ", measurement_units: " + measurement_units + ", price_per_unit: " + price_per_unit +  " }";
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

    static private void routeOrder(HttpExchange t) throws IOException {
        System.out.println("Read request accepted");
        Map<String, String> q = queryToMap(t.getRequestURI().getQuery());
        String qId = q.get("id");
        String r;
        String id = "";
        String request_id = "";
        String created_at = "";
        String client_name = "";
        String client_contact = "";
        String cnt = "";
        String status = "";
        try {
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery("select o.id, o.request_id, o.created_at, o.client_name, o.client_contact, i.cnt, c.id, c.good_code, c.good_name, c.good_description, c.measurement_units, c.price_per_unit, o.status_id  from orders o left join order_items i on i.order_id = o.id join catalog c on c.id = i.good_id where o.id = " + qId);
            List<String> items = new ArrayList<>();
            while (rs.next()) {
                id = "" + rs.getInt(0);
                request_id = rs.getString(1);
                created_at = "" + rs.getInt(2);
                client_name = rs.getString(3);
                client_contact = "" + rs.getInt(4);
                cnt = "" + rs.getInt(5);
                String gId = rs.getString(6);
                String good_code = "" + rs.getInt(7);
                String good_description = rs.getString(8);
                String measurement_units = "" + rs.getInt(9);
                String price_per_unit = getStatusById(rs.getInt(10));
                status = getStatusById(rs.getInt(11));
                r = "{id: " + gId + ", good_code: " + good_code + ", good_description: " + good_description + ", price_per_unit: " + price_per_unit +  " }";
                items.add(r);
            }
            String itemsJson = "{" + String.join(", \n", items) + "}";
            r = "{id: " + id + ", request_id: " + request_id + ", created_at: " + created_at + ", client_name: " + client_name + ", client_contact: " + client_contact + ", status: " + status +  " " +
                    "items: " +
                    "" +
                    itemsJson
                    + "}";
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

    static private void routeCreateOrder(HttpExchange t) throws IOException {
        System.out.println("Read request accepted");
        List<List<String>> q = postToList(buf(t.getRequestBody()));
        List<List<String>> positions = new ArrayList<>();
        String ord, r;
        String request_id = t.getRequestHeaders().getFirst("x-request-id");
        String client_name = "";
        String client_contact = "";
        for (List<String> p: q){
            if ("client_name".equals(p.get(0))) {
                request_id = p.get(1);
                continue;
            }
            if ("client_contact".equals(p.get(0))) {
                request_id = p.get(1);
                continue;
            }
            positions.add(p);
        }

        try {
            Statement _stmt=connection.createStatement();
            ResultSet rs=_stmt.executeQuery("select * from orders where request_id = \"" + request_id + " \"");
            r = "allready_exists";
            if (rs.next()) {
                t.sendResponseHeaders(200, r.length());
                System.out.println("order_allready_exists");
                return;
            }

            Statement _stmt2=connection.createStatement();
            String sql = "insert into orders (request_id, client_name, client_contact) values (\"" + request_id + "\", \"" + client_name + "\", \"" + client_contact + "\")";

            _stmt2.executeUpdate(sql);

            Statement _stmt3=connection.createStatement();
            ResultSet rs1 = _stmt3.executeQuery("select id from orders where request_id = \"" + request_id + " \"");
            rs1.next();
            String orderId = "" + rs1.getInt(0);

            Statement stmt=connection.createStatement();
            List<String> values = new ArrayList<>();

            for (List<String> p : positions) {
                String value = "(" + orderId + ", " + p.get(0) + ", " + p.get(1) + ")";
                values.add(value);
            }
            String valuesSql = String.join(", ", values);
            String insertSql = "insert into order_items (orderId, good_id, cnt) values " + valuesSql;
            System.out.println("request to database: " + sql);
            stmt.executeUpdate(insertSql);
            r = "";
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

    static private void routeCreateGood(HttpExchange t) throws IOException {
        System.out.println("routeCreateGood request accepted");
        String r;
        System.out.println("start try-catch");
        try {
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            System.out.println("postToMap done");
            String good_code = q.get("good_code");
            String good_name = q.get("good_name");
            String good_description = q.get("good_description");
            String measurement_units = q.get("measurement_units");
            String price_per_unit = q.get("price_per_unit");
            Statement _stmt=connection.createStatement();
            String selectSql = "select * from catalog where good_code = \"" + good_code + " \"";
            System.out.println("routeCreateGood request accepted, select sql: " + selectSql);
            ResultSet rs=_stmt.executeQuery(selectSql);
            r = "allready_exists";
            if (rs.next()) {
                t.sendResponseHeaders(409, r.length());
                System.out.println("good_allready_exists");
                return;
            }

            Statement stmt=connection.createStatement();
            String sql = "insert into catalog (good_code, good_name, good_description, measurement_units, price_per_unit) values (\"" + good_code + "\", \"" + good_name + "\", \"" + good_description + "\", \"" + measurement_units + "\", " + price_per_unit + ")";
            System.out.println("request to database: " + sql);
            stmt.executeUpdate(sql);
            r = "";
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            System.out.println("success");
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
        }
        System.out.println("send body");
        OutputStream os = t.getResponseBody();
        os.write(r.getBytes());
        os.close();
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
                .replaceAll("\r", "")
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
}
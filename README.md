# CC Web Server

A small HTTP server written in Java, built as a learning project.

Features include:

- HTTP request handling and routing
- Composable routers, handlers and middleware
- Static file serving through `StaticFileHandler` and `StripPrefixHandler`

## Getting Started

Requires JDK 25 and Maven.

Build the project and start the server:

```bash
mvn package
java -jar target/cc-web-server-1.0-SNAPSHOT.jar
```

The server starts on port `8080`.

Test the example endpoints using `curl`:

```bash
curl http://127.0.0.1:8080/
curl http://127.0.0.1:8080/time
curl -i http://127.0.0.1:8080/users/
curl -i http://127.0.0.1:8080/users/profile
curl "http://127.0.0.1:8080/time?format=HH:mm:ss"
curl "http://127.0.0.1:8080/time?format=yyyy-MM-dd"
```

## Routing

The router supports the following route patterns:

| Pattern       | Behavior                                                  |
|---------------|-----------------------------------------------------------|
| `/users`      | Exact route without a trailing slash                      |
| `/users/`     | Subtree route matching paths under `/users/`              |
| `/files/{$}`  | Terminal wildcard route                                   |
| `/users/{id}` | Captures the final path segment as `id`; exact route only |

### Simple Router

Create a router by registering routes with an HTTP method, a path pattern, and a handler.

```java
var router = new Router()
        .add(HttpMethod.GET, "/", (writer, request) -> {
            writer.headers().set("Content-Type", "text/plain");
            writer.write("Hello, World!");
        })
        .add(HttpMethod.GET, "/users/{id}", (writer, request) -> {
            writer.headers().set("Content-Type", "text/plain");
            writer.write(request.pathValue("id"));
        });
```

The first route responds to `GET /` with `Hello, World!`. The second captures the final path segment and makes it
available through `request.pathValue("id")`. For example, `GET /users/42` returns `42`.

### Mounting a Handler

Mount any `HttpHandler` under a path prefix. This allows routers, middleware-wrapped handlers, and other handlers to be
composed.

```java
var users = new Router()
        .add(HttpMethod.GET, "/", (writer, request) -> writer.write("Users"))
        .add(HttpMethod.GET, "/profile", (writer, request) -> writer.write("Profile"));

var router = new Router()
        .mount("/users", users);
```

The mounted handler receives requests under `/users/` with the mount prefix stripped from the request path.

```bash
curl http://127.0.0.1:8080/users/
curl http://127.0.0.1:8080/users/profile
```

### Static File Handler

`StaticFileHandler` serves files from a directory. Use `StripPrefixHandler` to remove a URL prefix before passing the
request to the file handler.

```java
var staticFiles = new StripPrefixHandler(
        new StaticFileHandler(Path.of("public")),
        "/static"
);

var router = new Router()
        .add(HttpMethod.GET, "/static/", staticFiles);
```

For example, with this directory structure:

```text
public/
└── hello.txt
```

The file can be requested at `/static/hello.txt`:

```bash
curl http://127.0.0.1:8080/static/hello.txt
```

The handler also supports directory listings.

## Middleware

Middleware wraps an `HttpHandler` to add behavior before or after request handling, such as logging, timing, or
modifying response headers.

Implement middleware by wrapping the next handler:

```java
Middleware logging = next -> (writer, request) -> {
    System.out.println(request.method() + " " + request.requestUrl().path());
    next.handleRequest(writer, request);
};
```

Compose middleware with `MiddlewareChain`. Middleware runs in registration order for incoming requests and unwinds in
reverse order after the next handler returns.

```java
var middleware = new MiddlewareChain(
        Main::loggingMiddleware,
        Main::timingMiddleware
);

HttpHandler handler = middleware.wrap(router);
```

## Query Parameters

The server parses URL query strings into query parameters, supporting multiple values for the same key. Query parameters
can be accessed through the request URL:

```java
var query = request.requestUrl().parseQuery();
var formats = query.get("format");
```

The `get()` method returns a list of values, allowing repeated parameters such as `?tag=java&tag=http` to be represented
without losing values.

## Example Application

`com.github.aaronbittel.Main` provides a runnable example of the server, including a greeting endpoint, a time endpoint,
static file serving, a mounted users router, and middleware. Use it as a starting point for experimenting with the
server's features.

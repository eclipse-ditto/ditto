## Eclipse Ditto :: Documentation

This folder contains the documentation of Eclipse Ditto.

It consists of following sub-folders which are explained here briefly:
* **getting-started** - "Hello World", basic usage of HTTP API
* **openapi** - OpenAPI (Swagger) Specification of Ditto's HTTP APIs

### Build jekyll

```bash
sudo apt-get install ruby-dev
sudo gem install --http-proxy http://localhost:3128 jekyll
```

### Service jekyll content

```bash
jekyll serve
```

Open browser at [http://localhost:4000](http://localhost:4000)

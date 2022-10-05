## Eclipse Ditto :: Documentation

This folder contains the documentation and [static website of Eclipse Ditto](https://www.eclipse.org/ditto/).

The documentation is based on [Jekyll](https://jekyllrb.com) and the fabulous [Jekyll Documentation Theme 6.0](http://idratherbewriting.com/documentation-theme-jekyll/).

### Build project site

For UNIX systems:

```bash
mvn clean install -Pbuild-documentation
```

### Setup jekyll watch for "live editing"

In order to edit the documentation 

#### Alternative 1: install Jekyll (UNIX or Mac OS)

##### Unix
Use that if you are on a UNIX system (or have the Ubuntu bash subsystem for Windows 10). 
If you're behind a proxy, you can use the `http_proxy` parameter.

```bash
sudo apt-get install build-essential ruby-dev libcurl3 zlib1g-dev
sudo gem install [--http-proxy http://localhost:3128] jekyll
sudo gem install [--http-proxy http://localhost:3128] jekyll-sitemap
sudo gem install [--http-proxy http://localhost:3128] html-proofer
```

If the installation of html-proofer fails, you may need the additional build dependency `zlib`.
```bash
sudo apt install zlib1g-dev
```

##### Mac OS
Follow the steps described in the link to install [Jekyll on macOS](https://jekyllrb.com/docs/installation/macos/)

###### Watch resources
Watch all resources and start local server serving the Jekyll content at [http://localhost:4000](http://localhost:4000):

```bash
cd src/main/resources
bundle install
bundle exec jekyll serve --verbose --unpublished
```

Validate that the HTML does not contain dead links, etc.:

```bash
htmlproofer --assume-extension --allow-hash-href --disable-external --enforce-https=false --ignore-urls "/http-api-doc.html.*/" src/main/resources/_site/
```

#### Alternative 2: use Maven (UNIX)

To serve the current documentation you only need to call `mvn gem:exec@jekyll-serve`.
It automatically monitors the filesystem and every local changes are generated on-demand on the local server [http://localhost:4000](http://localhost:4000).

#### Alternative 3: use Maven (Windows)

On a windows operating system you'll need to install Jekyll manually. If you don't have installed Jekyll on your machine you can just use the [PortableJekyll](https://github.com/madhur/PortableJekyll) project.
Just clone the GitHub repository and start the setpath.cmd which setups the necessary path entries into the CMD (Don't forget to copy them into the environment path variable to have the path set for every command prompt).

The maven build on Windows just executes the Jekyll process using the maven-exec plugin. This allows to also use maven build to build and serve the documentation on a windows machine.

To serve the current documentation you only need to call `mvn exec:exec@jekyll-serve`. It automatically monitors the filesystem and every local changes are generated on-demand on the local server [http://localhost:4000](http://localhost:4000).


## Eclipse Ditto :: Documentation

This folder contains the documentation and [static website of Eclipse Ditto](https://https://www.eclipse.org/ditto/).

The documentation is based on [Jekyll](https://jekyllrb.com) and the fabulous [Jekyll Documentation Theme 6.0](http://idratherbewriting.com/documentation-theme-jekyll/).

### Build project site

For UNIX systems:

```bash
mvn clean install -Pbuild-documentation
```

### Setup jekyll watch for "live editing"

In order to edit the documentation 

#### Alternative 1: install Jekyll (UNIX)

Use that if you are on a UNIX system (or have the Ubuntu bash subsystem for Windows 10). 

```bash
sudo apt-get install ruby-dev
sudo gem install --http-proxy http://localhost:3128 jekyll
```

Watch all resources and start local server serving the Jekyll content at [http://localhost:4000](http://localhost:4000):

```bash
cd src/main/resources
jekyll serve
```

#### Alternative 2: use Maven (UNIX)

To serve the current documentation you only need to call `mvn gem:exec@jekyll-serve`.
It automatically monitors the filesystem and every local changes are generated on-demand on the local server [http://localhost:4000](http://localhost:4000).

#### Alternative 3: use Maven (Windows)

On a windows operating system you'll need to install Jekyll manually. If you don't have installed Jekyll on your machine you can just use the [PortableJekyll](https://github.com/madhur/PortableJekyll) project.
Just clone the Github repository and start the setpath.cmd which setups the necessary path entries into the CMD (Don't forget to copy them into the environment path variable to have the path set for every command prompt).

The maven build on windows just executes the Jekyll process using the maven-exec plugin. This allows to also use maven build to build and serve the documentation on a windows machine.

To serve the current documentation you only need to call `mvn exec:exec@jekyll-serve`. It automatically monitors the filesystem and every local changes are generated on-demand on the local server [http://localhost:4000](http://localhost:4000).

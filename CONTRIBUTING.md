# How to contribute to Eclipse Ditto
 
First of all, thanks for considering to contribute to Eclipse Ditto. We really appreciate the time and effort you want to
spend helping to improve things around here.

In order to get you started as fast as possible we need to go through some organizational issues first, though.

## Legal Requirements

Ditto is an [Eclipse IoT](https://iot.eclipse.org) project and as such is governed by the Eclipse Development process.
This process helps us in creating great open source software within a safe legal framework.

For you as a contributor, the following preliminary steps are required in order for us to be able to accept your contribution:

* Sign the [Eclipse Foundation Contributor License Agreement](http://www.eclipse.org/legal/CLA.php).
In order to do so:
  * Obtain an Eclipse Foundation user ID. Anyone who currently uses Eclipse Bugzilla or Gerrit systems already has one of those.
If you don't already have an account simply [register on the Eclipse web site](https://dev.eclipse.org/site_login/createaccount.php).
  * Once you have your account, log in to the [projects portal](https://projects.eclipse.org/), select *My Account* and then the *Contributor License Agreement* tab.

* Add your GiHub username to your Eclipse Foundation account. Log in to Eclipse and go to [Edit my account](https://dev.eclipse.org/site_login/myaccount.php).

The easiest way to contribute code/patches/whatever is by creating a GitHub pull request (PR). When you do make sure that you *Sign-off* your commit records using the same email address used for your Eclipse account.

You do this by adding the `-s` flag when you make the commit(s), e.g.

    $> git commit -s -m "Shave the yak some more"

You can find all the details in the [Contributing via Git](http://wiki.eclipse.org/Development_Resources/Contributing_via_Git) document on the Eclipse web site.

## Codestyle

We use the [Google Java Style Guide](https://github.com/google/styleguide) where a formatter for Eclipse IDE is available. 

The only adjustment: use longer lines ("line split") with 120 characters instead of only 100.

## Making your Changes

* Fork the repository on GitHub
* Create a new branch for your changes
* Make your changes
* Make sure you include test cases for non-trivial features
* Make sure the test suite passes after your changes
* Please make sure to format your code with the above mentioned formatter
* Commit your changes into that branch
* Use descriptive and meaningful commit messages
* If you have more than one commit, squash your commits into a single commit 
* Make sure you use the `-s` flag when committing as explained above
* Push your changes to your branch in your forked repository

## Submitting the Changes

Submit a pull request via the normal GitHub UI.

## After Submitting

* Do not use your branch for any other development, otherwise further changes that you make will be visible in the PR.

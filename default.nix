# Edit this configuration file to define what should be installed on

# your system.  Help is available in the configuration.nix(5) man page
# and in the NixOS manual (accessible by running ‘nixos-help’).

{ stdenv, callPackage, openjdk, gradle, makeWrapper, tree, system, perl, ... }:
with stdenv;
let
  deps = mkDerivation {
    name = "deps";
    version = "0.0.8";
    nativeBuildInputs = [ openjdk gradle perl ];
    src = ./.;
    buildPhase = ''
      export GRADLE_USER_HOME=$(mktemp -d);
      gradle --no-daemon resolveDependencies;
    '';
    # keep only *.{pom,jar,sha1,nbm} and delete all ephemeral files with lastModified timestamps inside
    installPhase = ''
      find $GRADLE_USER_HOME/caches/modules-2 -type f -regex '.*\.\(jar\|pom\)' \
        | perl -pe 's#(.*/([^/]+)/([^/]+)/([^/]+)/[0-9a-f]{30,40}/([^/\s]+))$# ($x = $2) =~ tr|\.|/|; "install -Dm444 $1 \$out/maven/$x/$3/$4/$5" #e' \
        | sh
      mv  $out/maven/com/squareup/okio/okio/2.8.0/okio-jvm-2.8.0.jar $out/maven/com/squareup/okio/okio/2.8.0/okio-2.8.0.jar
    '';
    outputHashAlgo = "sha256";
    outputHashMode = "recursive";
    outputHash = "sha256-pFJtGF3wgYh83NUKZnRf4IUzF5uK8vtmpu9Aw55hQ0s=";
  };

  backend = mkDerivation {
    name = "winklink";
    src = ./.;
    buildInputs = [ openjdk gradle makeWrapper perl ];
    buildPhase = ''
      export GRADLE_USER_HOME=$(mktemp -d)

      export HOME="$NIX_BUILD_TOP/home"
      export JAVA_TOOL_OPTIONS="-Duser.home='$HOME'"

      mkdir -p .m2/repository/

      cp -r ${deps}/maven/* .m2/repository

      export M2_HOME=.m2

      ls ${deps}

      # point to offline repo
      sed -i "s#mavenLocal()#mavenLocal();maven { url '${deps}/maven' }#g" build.gradle


      # point to offline repo
      sed -i "s#mavenLocal()#mavenLocal();maven { url '${deps}/maven' }#g" node/build.gradle


      # point to offline repo
      sed -i "s#mavenLocal()#mavenLocal();maven { url '${deps}/maven' }#g" settings.gradle


      gradle --offline --info --no-daemon build -x test
    '';

    installPhase = ''
      mkdir -p $out/lib
      mkdir -p $out/bin
      cp node/build/libs/*.jar $out/lib
      makeWrapper ${openjdk}/bin/java $out/bin/winklink-node --add-flags "-jar $out/lib/node-v1.0.jar"
    '';
  };

in backend

# SPDX-FileCopyrightText: 2021 Serokell <https://serokell.io/>
#
# SPDX-License-Identifier: CC0-1.0

{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs";
    flake-utils.url = "github:numtide/flake-utils";
    devshell = {
      url = "github:numtide/devshell";
      inputs = {
        nixpkgs.follows = "nixpkgs";
        flake-utils.follows = "flake-utils";
      };
    };
    npmlock2nix = {
      url = "github:nix-community/npmlock2nix";
      flake = false;
    };
  };

  outputs = { self, nixpkgs, flake-utils, devshell, npmlock2nix, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [
            devshell.overlays.default
            (final: prev: {
              npmlock2nix = import npmlock2nix { pkgs = prev; };
            })
          ];
          config.allowUnfree = true;
        };
      in {
        packages = {
          default = pkgs.callPackage ./default.nix { };
          front-end = pkgs.npmlock2nix.build {
            src = ./node/webapp;
            installPhase = "cp -r ../src/main/resources/static $out";
            buildCommands = [ "npm run build" ];
            node_modules_attrs = { nativeBuildInputs = [ pkgs.python2 ]; };
          };

        };
        devShell = pkgs.devshell.mkShell {
          env = [
            {
              name = "NIX_LD_LIBRARY_PATH";
              value = pkgs.lib.makeLibraryPath [ pkgs.stdenv.cc.cc ];
            }
            {
              name = "NIX_LD";
              value = builtins.readFile
                "${pkgs.stdenv.cc}/nix-support/dynamic-linker";
            }
          ];
          commands = [
            {
              category = "Programming language support";
              package = pkgs.openjdk17;
              help = ''
                1. use gradle assemble to install dependency first
                2. use gradle build -x test for dev build 
                3. go to node/build/lib to find jars 
              '';
            }
            {
              category = "Java package manager";
              package = pkgs.gradle;
              name = "gradle";
              help = ''
                1. use gradle assemble to install dependency first
                2. use gradle build -x test for dev build 
                3. go to node/build/lib to find jars 
              '';
            }
            {
              category = "Java package manager";
              package = pkgs.nodejs-14_x;
              name = "nodejs";
            }
            { package = pkgs.python2; }
          ];
        };
      });
}

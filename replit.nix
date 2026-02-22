{ pkgs }:
{
  deps = [
    pkgs.openjdk21
    pkgs.gradle
    pkgs.sqlite
  ];
}

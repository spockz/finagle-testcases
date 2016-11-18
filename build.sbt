name := "finagle-testcases"

version := "1.0"

scalaVersion := "2.11.5"

val finagleVersion = "6.39.0"

libraryDependencies += "com.twitter" %% "finagle-http" % finagleVersion
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test"
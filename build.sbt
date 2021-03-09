name := "work-item-repo"

enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)

makePublicallyAvailableOnBintray := true

majorVersion := 8

scalaVersion := "2.12.13"

libraryDependencies ++= LibDependencies.compile ++ LibDependencies.test

resolvers += Resolver.typesafeRepo("releases")

PlayCrossCompilation.playCrossCompilationSettings

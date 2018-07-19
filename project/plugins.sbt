credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

resolvers ++= Seq(
  Resolver.bintrayIvyRepo("hmrc", "sbt-plugin-releases"),
  Resolver.typesafeRepo("releases")
)

addSbtPlugin("uk.gov.hmrc" % "sbt-settings" % "3.2.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.12.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "0.10.0")

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  "Madoushi sbt-plugins" at "https://dl.bintray.com/madoushi/sbt-plugins/"
)

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.4.0")

addSbtPlugin("com.eed3si9n"      % "sbt-assembly"            % "0.14.1")
addSbtPlugin("me.lessis"         %  "bintray-sbt"            % "0.3.0")
addSbtPlugin("com.typesafe.sbt"  %  "sbt-git"                % "0.8.5")
addSbtPlugin("org.scalastyle"    %% "scalastyle-sbt-plugin"  % "0.8.0")
addSbtPlugin("io.spray"          %  "sbt-revolver"           % "0.8.0")
addSbtPlugin("com.typesafe.sbt"  %  "sbt-twirl"              % "1.1.1")

// web
addSbtPlugin("com.typesafe.sbt"    % "sbt-web"          % "1.3.0")
addSbtPlugin("com.typesafe.sbt"    % "sbt-js-engine"    % "1.1.3")
addSbtPlugin("net.ground5hark.sbt" % "sbt-concat"       % "0.1.8")
addSbtPlugin("com.typesafe.sbt"    % "sbt-jshint"       % "1.0.3")
addSbtPlugin("com.typesafe.sbt"    % "sbt-mocha"        % "1.1.0")
addSbtPlugin("com.typesafe.sbt"    % "sbt-uglify"       % "1.0.3")
addSbtPlugin("org.madoushi.sbt"    % "sbt-sass"         % "0.9.3")
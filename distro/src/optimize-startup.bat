@echo off

:: ###############################################################################
:: #                                                                             #
:: #                     Optimize Production Startup Script                      #
:: #                                                                             #
:: ###############################################################################
::
:: Note: This script is supposed to be used in production
:: to start-up Optimize. For further information please consult
:: the documentation: https://docs.camunda.org/optimize/latest/technical-guide/installation/
::
:: Optionally, you can overwrite the default JVM options by setting the `OPTIMIZE_JAVA_OPTS`
:: variable.

set BASEDIR=%~dp0
cd "%BASEDIR%"

:: now set the path to java
IF DEFINED JAVA_HOME (
  set JAVA="%JAVA_HOME%\bin\java.exe"
) ELSE (
  set JAVA=java
)

:: check if there are custom JVM options set.
IF NOT DEFINED OPTIMIZE_JAVA_OPTS (
  set OPTIMIZE_JAVA_OPTS=-Xms1024m -Xmx1024m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m
)

:: check if there are custom JVM options set.
set DEBUG_JAVA_OPTS=
IF "%1"=="debug" (

  set DEBUG_PORT=9999
  set DEBUG_JAVA_OPTS=-Xdebug -agentlib:jdwp=transport=dt_socket,address=%DEBUG_PORT%,server=y,suspend=n
)

:: Set up the optimize classpaths, i.e. add the environment folder, all jars in the
:: plugin directory and the optimize jar
set OPTIMIZE_CLASSPATH="%BASEDIR%environment;%BASEDIR%plugin\*;%BASEDIR%optimize-backend-${project.version}.jar"

echo.
echo Starting Camunda Optimize ${project.version}
echo.

:: start optimize
set OPTIMIZE_CMD=%JAVA% %OPTIMIZE_JAVA_OPTS% -cp %OPTIMIZE_CLASSPATH% %DEBUG_JAVA_OPTS% -Dfile.encoding=UTF-8 org.camunda.optimize.Main
call %OPTIMIZE_CMD%
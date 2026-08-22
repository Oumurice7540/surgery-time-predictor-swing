@echo off
setlocal
set "MVNW_DIR=%~dp0"
set "MVNW_BASE=%MVNW_DIR:~0,-1%"
set "JAVA_EXE=java.exe"
if not "%JAVA_HOME%"=="" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not exist "%MVNW_BASE%\.mvn\wrapper\maven-wrapper.jar" (
  echo Maven Wrapper JAR is missing: %MVNW_BASE%\.mvn\wrapper\maven-wrapper.jar
  exit /b 1
)
pushd "%MVNW_BASE%"
"%JAVA_EXE%" -classpath "%MVNW_BASE%\.mvn\wrapper\maven-wrapper.jar" "-Dmaven.multiModuleProjectDirectory=%MVNW_BASE%" org.apache.maven.wrapper.MavenWrapperMain %*
set "MVNW_EXIT=%ERRORLEVEL%"
popd
exit /b %MVNW_EXIT%

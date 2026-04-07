@ECHO OFF
SETLOCAL

SET "MAVEN_PROJECTBASEDIR=%~dp0"
IF "%MAVEN_PROJECTBASEDIR:~-1%"=="\" SET "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

SET "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"

IF NOT EXIST "%WRAPPER_JAR%" (
  echo Maven Wrapper JAR not found at %WRAPPER_JAR%
  EXIT /B 1
)

WHERE java >NUL 2>NUL
IF %ERRORLEVEL% EQU 0 (
  SET "JAVA_CMD=java"
) ELSE (
  IF DEFINED JAVA_HOME (
    SET "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
  ) ELSE (
    echo java.exe not found in PATH or JAVA_HOME
    EXIT /B 1
  )
)

"%JAVA_CMD%" %MAVEN_OPTS% ^
  -classpath "%WRAPPER_JAR%" ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  org.apache.maven.wrapper.MavenWrapperMain %*

ENDLOCAL

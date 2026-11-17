# Ponte do Customizr em container:
#   docker build -t customizr-ponte .
#   docker run -d --restart unless-stopped -p 55070:55070 --name ponte customizr-ponte
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY customizr-ponte.jar .
EXPOSE 55070
CMD ["java", "-Xmx128m", "-jar", "customizr-ponte.jar", "55070"]

FROM booking-portal-tests:latest AS build
WORKDIR /build
RUN mvn package -DskipTests -U -B

FROM repo-webapp:latest AS runtime-base

FROM runtime-base
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh
ENTRYPOINT ["/app/entrypoint.sh"]
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]

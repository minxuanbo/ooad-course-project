FROM python:3.12-slim
WORKDIR /app
COPY integrations/course/requirements-deployment.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt \
    && groupadd --gid 10001 integration \
    && useradd --uid 10001 --gid integration --no-create-home integration
COPY integrations/course/*.py integrations/course/
COPY integrations/course/*.json integrations/course/
COPY scripts/initialize_course_integration.py scripts/initialize_business_catalog.py scripts/build-prototype-applications.py scripts/
COPY ["市政公司应用软件功能权限清单.xlsx", "/app/市政公司应用软件功能权限清单.xlsx"]
COPY deploy/initialize_deployment.py deploy/initialize_deployment.py
USER 10001:10001
ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app
EXPOSE 8085
CMD ["waitress-serve", "--listen=0.0.0.0:8085", "--threads=4", "--channel-timeout=30", "--max-request-body-size=1048576", "--no-clear-untrusted-proxy-headers", "--call", "integrations.course.deployment_gateway:create_app"]

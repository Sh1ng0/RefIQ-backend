FROM rocker/r-ver:4.3.2

RUN apt-get update && apt-get install -y \
    libcurl4-openssl-dev \
    libssl-dev \
    libxml2-dev \
    && rm -rf /var/lib/apt/lists/*

RUN R -e "install.packages(c('refineR', 'plumber', 'jsonlite', 'arrow'))"

WORKDIR /app

COPY api_plumber.R .

EXPOSE 8000

ENTRYPOINT ["R", "-e", "pr <- plumber::plumb('api_plumber.R'); pr$run(host='0.0.0.0', port=8000)"]
# Usamos una imagen estable de R para 2026
FROM rocker/r-ver:4.3.2

# Instalamos dependencias del sistema para paquetes de red en R
RUN apt-get update && apt-get install -y \
    libcurl4-openssl-dev \
    libssl-dev \
    libxml2-dev \
    && rm -rf /var/lib/apt/lists/*

# Instalamos las librerías de R: refineR (el modelo), plumber (la API), jsonlite (JSON) y arrow (Parquet)
RUN R -e "install.packages(c('refineR', 'plumber', 'jsonlite', 'arrow'))"

# Creamos el directorio de la app
WORKDIR /app

# Copiamos el script de la lógica (transformado a Plumber)
COPY api_plumber.R .

# Exponemos el puerto por el que escuchará R
EXPOSE 8000

# Comando para arrancar la API
ENTRYPOINT ["R", "-e", "pr <- plumber::plumb('api_plumber.R'); pr$run(host='0.0.0.0', port=8000)"]
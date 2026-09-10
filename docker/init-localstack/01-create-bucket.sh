#!/bin/bash
echo "🔥 Iniciando configuración de S3 en LocalStack..."


awslocal s3 mb s3://refiq-clinical-data-dev

echo "✅ Bucket 'refiq-clinical-data-dev' creado correctamente."
FROM nvidia/cuda:12.8.1-cudnn-runtime-ubuntu22.04
ENV DEBIAN_FRONTEND=noninteractive PYTHONUNBUFFERED=1
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-pip git libsndfile1 && rm -rf /var/lib/apt/lists/*
WORKDIR /srv/t2s
COPY requirements.txt .
RUN python3 -m pip install --no-cache-dir -r requirements.txt
COPY server.py .
EXPOSE 8080
CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8080"]

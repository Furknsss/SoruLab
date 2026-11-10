FROM python:3.11-slim
WORKDIR /srv/twin
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py math_gate.py ./
EXPOSE 8765
CMD ["uvicorn", "app:api", "--host", "0.0.0.0", "--port", "8765"]

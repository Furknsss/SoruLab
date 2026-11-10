FROM python:3.11-slim
WORKDIR /srv/twin
COPY requirements.txt requirements-image.txt ./
# Install the image SDK so the owner can opt in later; no API call occurs
# without SORULAB_NANO_BANANA_ENABLE=1 and a server-side key.
RUN pip install --no-cache-dir -r requirements-image.txt
COPY app.py math_gate.py ./
EXPOSE 8765
CMD ["uvicorn", "app:api", "--host", "0.0.0.0", "--port", "8765"]

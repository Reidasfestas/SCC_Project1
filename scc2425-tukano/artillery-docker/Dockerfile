# Use Node.js as the base image
FROM node:18

# Set the working directory inside the container
WORKDIR /app

# Install Artillery globally
RUN npm install -g artillery

# Copy your YAML scripts into the container
COPY ./scripts /app/scripts

# Default command to keep the container running
CMD ["tail", "-f", "/dev/null"]

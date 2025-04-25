import os
import time
from pyubx2 import UBXReader

def extract_unformatted_lat_lon(file_path):
    """Extracts unformatted latitude and longitude from three NAV-PVT messages (start, middle, and end)."""
    try:
        with open(file_path, 'rb') as ubx_file:
            ubx_reader = UBXReader(ubx_file, protfilter=2)  # Only UBX messages
            nav_pvt_messages = []
            for raw_data, parsed_message in ubx_reader:
                if parsed_message and parsed_message.identity == "NAV-PVT":
                    nav_pvt_messages.append(parsed_message)

            if not nav_pvt_messages:
                print(f"No NAV-PVT messages found in {file_path}")
                return

            middle_index = len(nav_pvt_messages) // 2
            selected_messages = [
                nav_pvt_messages[0],  # Start
                nav_pvt_messages[middle_index],  # Middle
                nav_pvt_messages[-1]  # End
            ]

            print(f"\nExtracted from {file_path}:")
            for index, message in enumerate(selected_messages):
                data = {
                    "GPS UTC Time": f"{message.year}-{message.month:02d}-{message.day:02d} "
                                    f"{message.hour:02d}:{message.min:02d}:{message.second:02d}",
                    "Unformatted Latitude": message.lat,
                    "Unformatted Longitude": message.lon,
                }
                print(f"Message {index + 1}: {data}")
    except Exception as e:
        print(f"Error processing {file_path}: {e}")

def monitor_directory(directory_path):
    """Continuously monitors a directory for new UBX files and processes only newly created ones."""
    processed_files = set()  # Keep track of already processed files
    start_time = time.time()  # Record the script start time

    while True:
        try:
            # List all .ubx files in the directory
            all_files = {f for f in os.listdir(directory_path) if f.endswith('.ubx')}

            # Filter files based on creation time and unprocessed status
            new_files = {
                f for f in all_files
                if f not in processed_files and os.path.getctime(os.path.join(directory_path, f)) > start_time
            }

            if new_files:
                print(f"New files detected: {new_files}")

            # Process each new file
            for file_name in new_files:
                file_path = os.path.join(directory_path, file_name)
                extract_unformatted_lat_lon(file_path)
                processed_files.add(file_name)  # Mark the file as processed

            # Wait for a short interval before checking again
            time.sleep(5)
        except KeyboardInterrupt:
            print("Monitoring stopped.")
            break
        except Exception as e:
            print(f"Error while monitoring directory: {e}")

# Example usage
directory_path = r"/storage/emulated/0/Documents/gpslogsdata"  # Replace with your directory path
monitor_directory(directory_path)

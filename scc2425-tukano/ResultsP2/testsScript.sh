artillery run --output CachedResults/post_user.json ../yamlTests/users/post_user.yaml

wait

artillery run --output CachedResults/get_user.json ../yamlTests/users/get.yaml

wait

artillery run --output CachedResults/get_pattern.json ../yamlTests/users/get_pattern.yaml

wait

artillery run --output CachedResults/post_short.json ../yamlTests/shorts/post_short.yaml

wait

artillery run --output CachedResults/get_shortId.json ../yamlTests/shorts/get_shortId.yaml

wait

artillery run --output CachedResults/get_shorts.json ../yamlTests/shorts/get_shorts.yaml

wait

artillery run --output CachedResults/follow_user.json ../yamlTests/shorts/follow_user.yaml

wait

artillery run --output CachedResults/like_post.json ../yamlTests/shorts/like_post.yaml

wait

artillery run --output CachedResults/get_followers.json ../yamlTests/shorts/get_followers.yaml

wait

artillery run --output CachedResults/get_feed.json ../yamlTests/shorts/get_feed.yaml

wait

artillery run --output CachedResults/post_blobs.json ../yamlTests/blobs/post_blobs.yaml

wait

artillery run --output CachedResults/dowload_blobs.json ../yamlTests/blobs/download_blobs.yaml

wait

artillery run --output UnCachedResults/post_user.json ../yamlTests/users/post_user.yaml

wait

artillery run --output UnCachedResults/get_user.json ../yamlTests/users/get.yaml

wait

artillery run --output UnCachedResults/get_pattern.json ../yamlTests/users/get_pattern.yaml

wait

artillery run --output UnCachedResults/post_short.json ../yamlTests/shorts/post_short.yaml

wait

artillery run --output UnCachedResults/get_shortId.json ../yamlTests/shorts/get_shortId.yaml

wait

artillery run --output UnCachedResults/get_shorts.json ../yamlTests/shorts/get_shorts.yaml

wait

artillery run --output UnCachedResults/follow_user.json ../yamlTests/shorts/follow_user.yaml

wait

artillery run --output UnCachedResults/like_post.json ../yamlTests/shorts/like_post.yaml

wait

artillery run --output UnCachedResults/get_followers.json ../yamlTests/shorts/get_followers.yaml

wait

artillery run --output UnCachedResults/get_feed.json ../yamlTests/shorts/get_feed.yaml

wait

artillery run --output UnCachedResults/post_blobs.json ../yamlTests/blobs/post_blobs.yaml

wait

artillery run --output UnCachedResults/dowload_blobs.json ../yamlTests/blobs/download_blobs.yaml

wait

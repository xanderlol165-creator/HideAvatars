version = "1.0.0" 
description = "Instantly toggle all avatars on and off." 

aliucord {
    // This line forces the app to show your name instead of 'yournamehere'
    author("Xander") 

    changelog.set(
        """
        # 1.0.0
        * Added a toggle switch to hide all avatars!
        """.trimIndent(),
    )
    
    deploy.set(true)
}

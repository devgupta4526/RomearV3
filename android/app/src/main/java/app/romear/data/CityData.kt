package app.romear.data

import app.romear.R

data class CityItem(
    val id: String, val title: String, val subtitle: String, val image: Int,
    val kind: Kind, val description: String = "", val location: String = "Rome",
    val category: String = "", val members: Int = 0,
)
enum class Kind { CLUB, EVENT, VENUE, PERSON, POST, POOL, PLACE }

object CityData {
    val clubs = listOf(
        CityItem("runners", "City Runners Club", "Running · 426 members", R.drawable.photo_runners, Kind.CLUB,
            "A friendly running community for all levels, with weekly runs and city routes across Rome.", "Riverside", "Running", 426),
        CityItem("cafe-circle", "Riverside Café Society", "Cafés · 278 members", R.drawable.photo_cafe, Kind.CLUB,
            "Good coffee, thoughtful conversations, and a new corner of the city every week.", "Trastevere", "Cafés", 278),
        CityItem("tech", "Tech & Startups Circle", "Tech · 512 members", R.drawable.photo_tech, Kind.CLUB,
            "Meet local makers, share what you are building, and find your next collaborator.", "Monti", "Tech", 512),
        CityItem("urban", "Urban Explorers", "Culture · 3,907 members", R.drawable.photo_colosseum, Kind.CLUB,
            "Take the slower route. Discover architecture, art, and the stories behind Rome.", "Centro Storico", "Culture", 3907),
    )
    val events = listOf(
        CityItem("market", "Riverside Night Market", "Sat, 28 Nov · 5:00 PM", R.drawable.photo_market, Kind.EVENT,
            "An evening of independent makers, street food, and live music beneath the lights. Come with friends or meet someone new.", "Riverside Promenade", "Food & culture"),
        CityItem("sunday-run", "Sunday Long Run", "Sun, 29 Nov · 6:00 AM", R.drawable.photo_runners, Kind.EVENT,
            "A relaxed 6 km loop along the river. All paces welcome. Bring water and stay for a coffee afterwards.", "Central Park, Rome", "Running"),
        CityItem("coffee-meet", "Coffee & Conversation", "Sun, 29 Nov · 10:00 AM", R.drawable.photo_cafe, Kind.EVENT,
            "A small table, a great coffee, and new people from your city. Hosted by Riverside Café Society.", "Riverside Café", "Cafés"),
    )
    val venues = listOf(
        CityItem("riverside", "Riverside Café", "Café · Italian · Riverside", R.drawable.photo_cafe_river, Kind.VENUE,
            "A relaxed all-day café by the river, known for seasonal food, specialty coffee, and sunlit tables.", "Lungotevere, Rome", "Cafés"),
        CityItem("brew", "Brew & Board", "Café · Board games · Monti", R.drawable.photo_cafe, Kind.VENUE,
            "Pick a game, order something warm, and make an afternoon of it.", "Monti, Rome", "Cafés"),
        CityItem("studio", "The Makers Studio", "Workspace · Creative community", R.drawable.photo_tech, Kind.VENUE,
            "A welcoming place to work, meet, and turn ideas into something real.", "Prati, Rome", "Workspaces"),
    )
    val people = listOf(
        CityItem("aarav", "Aarav Mehta", "Running · Cafés · Travel", R.drawable.avatar_aarav, Kind.PERSON,
            "City explorer, runner and café lover. Always up for a good run, a new café or a weekend city adventure.", "1.2 km · 3 mutual clubs"),
        CityItem("riya", "Riya Sharma", "Books · Cafés · Art", R.drawable.avatar_riya, Kind.PERSON,
            "Collecting good books, little cafés, and stories from around the city.", "2.5 km · 2 mutual clubs"),
        CityItem("kabir", "Kabir Singh", "Tech · Startups · Running", R.drawable.avatar_aarav, Kind.PERSON,
            "Building things and finding new routes through Rome.", "2.8 km · 2 mutual clubs"),
        CityItem("meera", "Meera Joshi", "Music · Events · Travel", R.drawable.avatar_riya, Kind.PERSON,
            "Live music, weekend escapes, and a city full of possibilities.", "3.1 km · 1 mutual club"),
    )
    val posts = listOf(
        CityItem("sunset", "Sunset Run by the Tiber", "Riya Sharma · Riverside Runners · 2h", R.drawable.photo_run, Kind.POST,
            "Great 6k with the Riverside Runners today. The weather was perfect! Who’s joining us next Sunday?", "Lungotevere, Rome", "Running"),
        CityItem("coffee", "A slower kind of Saturday", "Riverside Café · 4h", R.drawable.photo_cafe, Kind.POST,
            "Fresh coffee, a sunlit table, and nowhere else to be. Your weekend starts here.", "Riverside Café", "Cafés"),
        CityItem("weekend", "Meet you under the lights", "Urban Explorers · Yesterday", R.drawable.photo_market, Kind.POST,
            "Found the loveliest little market by the river. Save this for your next evening out.", "Riverside Promenade", "Culture"),
    )
    val pools = listOf(
        CityItem("table-tennis", "Table Tennis Match", "Today · 7:00 PM · 2 of 5 joined", R.drawable.photo_table_tennis, Kind.POOL,
            "Looking for a few friendly players. Beginners welcome; we have spare paddles.", "Sadar Sports Arena", "Sports"),
        CityItem("cycling", "Cycling Buddy", "Tomorrow · 6:30 AM · 1 of 3 joined", R.drawable.photo_run, Kind.POOL,
            "An easy morning ride along the river, with a coffee stop on the way back.", "Riverside", "Outdoors"),
        CityItem("study", "Study Group", "Tomorrow · 4:00 PM · 2 of 4 joined", R.drawable.photo_tech, Kind.POOL,
            "A quiet afternoon to focus together. Bring your laptop or a book.", "Brew & Board", "Learning"),
    )
    val places = listOf(
        venues.first(),
        CityItem("colosseum", "Colosseum", "Landmark · Visited 12 Nov", R.drawable.photo_colosseum, Kind.PLACE),
        CityItem("borghese", "Villa Borghese", "Park · Visited 2 Nov", R.drawable.photo_park, Kind.PLACE),
    )
    val all get() = clubs + events + venues + people + posts + pools + places
    fun find(id: String) = all.firstOrNull { it.id == id }
    fun referencePhoto(key: String?) = when(key) {
        "portrait" -> R.drawable.story_cafe
        "run" -> R.drawable.photo_run
        "cafe" -> R.drawable.photo_cafe
        "market" -> R.drawable.photo_market
        else -> null
    }
}

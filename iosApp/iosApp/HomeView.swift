import SwiftUI
import shared
import UIKit

// MARK: - Premium Home (Today) Dashboard

/// Today / Home screen for Nemory iOS.
/// Presents a proactive, glassmorphic personal vault dashboard backed by the
/// shared Kotlin Multiplatform domain, encrypted Room database, and on-device intelligence.
struct HomeView: View {
    private let bridge = IosVaultBridge.shared

    @StateObject private var clipboard = ClipboardWatcher.shared
    @StateObject private var calendar = CalendarConnector.shared
    @StateObject private var reminders = RemindersConnector.shared
    @StateObject private var watch = WatchBridge.shared
    @StateObject private var pulse = LifePulseEngine.shared
    @StateObject private var liveActivities = LiveActivityManager.shared

    @State private var items: [VaultItemSummary] = []
    @State private var collections: [CollectionSummary] = []
    @State private var briefing: BriefingSnapshot?
    @State private var showingCaptureSheet = false
    @State private var quickQuery = ""
    @State private var navigateToBrain = false

    private var greetingText: String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5..<12: return "Good morning"
        case 12..<17: return "Good afternoon"
        default: return "Good evening"
        }
    }

    private var formattedTodayDate: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEEE, MMMM d"
        return formatter.string(from: Date())
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Header Greeting
                    VStack(alignment: .leading, spacing: 6) {
                        Text(formattedTodayDate.uppercased())
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.secondary)
                            .tracking(1.2)

                        HStack {
                            Text(greetingText)
                                .font(.system(size: 32, weight: .bold, design: .rounded))
                            Spacer()
                            NavigationLink {
                                ContentView()
                            } label: {
                                Image(systemName: "text.bubble.fill")
                                    .font(.title3)
                                    .foregroundStyle(.secondary)
                                    .padding(8)
                                    .background(.ultraThinMaterial, in: Circle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("openSample")
                            .accessibilityLabel("Open Sample")

                            NavigationLink {
                                SettingsDemoView()
                            } label: {
                                Image(systemName: "gearshape.fill")
                                    .font(.title3)
                                    .foregroundStyle(.secondary)
                                    .padding(8)
                                    .background(.ultraThinMaterial, in: Circle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20)
                    .padding(.top, 12)

                    // Dynamic Island & Live Activities Display
                    if liveActivities.isFlightActivityActive, let state = liveActivities.activeFlightState {
                        DynamicIslandFlightView(state: state)
                            .padding(.horizontal, 20)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    if liveActivities.isDeliveryActivityActive, let state = liveActivities.activeDeliveryState {
                        DynamicIslandDeliveryView(state: state)
                            .padding(.horizontal, 20)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    // Quick Ask Brain Bar
                    NavigationLink {
                        BrainView()
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "sparkles")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(LinearGradient(
                                    colors: [Color.accentColor, Color.purple],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ))
                            Text("Ask your vault anything…")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Spacer()
                            Image(systemName: "arrow.right.circle.fill")
                                .font(.title3)
                                .foregroundStyle(Color.accentColor)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.primary.opacity(0.08), lineWidth: 0.5)
                        )
                        .shadow(color: Color.black.opacity(0.03), radius: 6, y: 3)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 20)
                    .accessibilityIdentifier("homeAskBrainBar")

                    // Smart Clipboard Sentry Banner
                    if let snippet = clipboard.activeSnippet {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(Color.orange.opacity(0.18))
                                    .frame(width: 38, height: 38)
                                Image(systemName: "doc.on.clipboard.fill")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundStyle(.orange)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(snippet.category.uppercased())
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundStyle(.orange)
                                    Text("COPIED ON IPHONE")
                                        .font(.system(size: 9, weight: .semibold))
                                        .foregroundStyle(.secondary)
                                }
                                Text(snippet.displayTitle)
                                    .font(.subheadline.weight(.semibold))
                                    .lineLimit(1)
                            }
                            Spacer()
                            Button {
                                saveClipboardSnippet(snippet)
                            } label: {
                                Text("Save")
                                    .font(.caption.weight(.bold))
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(Color.accentColor, in: Capsule())
                                    .foregroundStyle(.white)
                            }
                            .buttonStyle(.plain)

                            Button {
                                clipboard.dismiss()
                            } label: {
                                Image(systemName: "xmark")
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(.secondary)
                                    .padding(6)
                                    .background(.ultraThinMaterial, in: Circle())
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(12)
                        .background(Color.orange.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color.orange.opacity(0.25), lineWidth: 0.8)
                        )
                        .padding(.horizontal, 20)
                    }

                    // Today's Life Pulse Card
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(Color.green)
                                    .frame(width: 8, height: 8)
                                Text("TODAY'S LIFE PULSE")
                                    .font(.system(size: 11, weight: .bold))
                                    .tracking(1.0)
                                    .foregroundStyle(Color.green)
                            }
                            Spacer()
                            NavigationLink {
                                ConnectorsHubView()
                            } label: {
                                HStack(spacing: 4) {
                                    Text("Ecosystem")
                                        .font(.caption.weight(.semibold))
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 10, weight: .bold))
                                }
                                .foregroundStyle(Color.accentColor)
                            }
                            .buttonStyle(.plain)
                        }

                        Text(pulse.proactiveInsight)
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(.primary)

                        // Enriched Context Chips
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                if let next = calendar.upcomingEvents.first {
                                    HStack(spacing: 5) {
                                        Image(systemName: "calendar")
                                            .font(.caption2)
                                        Text(next.title.prefix(20))
                                            .font(.caption2.weight(.medium))
                                            .lineLimit(1)
                                    }
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color.blue.opacity(0.12), in: Capsule())
                                    .foregroundStyle(Color.blue)
                                }

                                HStack(spacing: 5) {
                                    Image(systemName: "checklist")
                                        .font(.caption2)
                                    Text("\(reminders.syncedCount > 0 ? "\(reminders.syncedCount) Synced" : "Reminders Ready")")
                                        .font(.caption2.weight(.medium))
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.purple.opacity(0.12), in: Capsule())
                                .foregroundStyle(Color.purple)

                                HStack(spacing: 5) {
                                    Image(systemName: "applewatch")
                                        .font(.caption2)
                                    Text(watch.isPaired ? "Watch Paired" : "Watch Ready")
                                        .font(.caption2.weight(.medium))
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.teal.opacity(0.12), in: Capsule())
                                .foregroundStyle(Color.teal)
                            }
                        }
                    }
                    .padding(14)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(Color.primary.opacity(0.08), lineWidth: 0.5)
                    )
                    .padding(.horizontal, 20)

                    // Primary Hub Grid
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 14) {
                        NavigationLink {
                            VaultListView()
                        } label: {
                            DashboardHubCard(
                                title: "Vault",
                                subtitle: "\(items.count) item\(items.count == 1 ? "" : "s")",
                                systemImage: "archivebox.fill",
                                gradient: [Color.blue, Color.cyan]
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("openVault")

                        NavigationLink {
                            CollectionsListView()
                        } label: {
                            DashboardHubCard(
                                title: "Collections",
                                subtitle: "\(collections.count) folder\(collections.count == 1 ? "" : "s")",
                                systemImage: "folder.fill",
                                gradient: [Color.indigo, Color.purple]
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("openCollections")

                        NavigationLink {
                            BrainView()
                        } label: {
                            DashboardHubCard(
                                title: "Brain",
                                subtitle: "Apple Intelligence",
                                systemImage: "brain.head.profile",
                                gradient: [Color.purple, Color.pink]
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("openBrain")

                        NavigationLink {
                            BriefingView()
                        } label: {
                            DashboardHubCard(
                                title: "Briefing",
                                subtitle: "Daily digest",
                                systemImage: "sunrise.fill",
                                gradient: [Color.orange, Color.amberAccent]
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("openBriefing")
                    }
                    .padding(.horizontal, 20)

                    // Vault Health & Completeness Audit Card
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            HStack(spacing: 6) {
                                Image(systemName: "shield.lefthalf.filled")
                                    .font(.caption.weight(.bold))
                                Text("VAULT HEALTH & AUDIT")
                                    .font(.system(size: 11, weight: .bold))
                                    .tracking(1.0)
                            }
                            .foregroundStyle(Color.indigo)
                            Spacer()
                            Text("85% COMPLETE")
                                .font(.system(size: 10, weight: .heavy))
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(Color.indigo.opacity(0.15), in: Capsule())
                                .foregroundStyle(Color.indigo)
                        }

                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(Color.primary.opacity(0.08))
                                    .frame(height: 6)
                                Capsule()
                                    .fill(LinearGradient(colors: [Color.indigo, Color.blue], startPoint: .leading, endPoint: .trailing))
                                    .frame(width: geo.size.width * 0.85, height: 6)
                            }
                        }
                        .frame(height: 6)

                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.caption)
                                .foregroundStyle(Color.orange)
                            Text("Add car insurance policy to reach 100% life protection.")
                                .font(.caption2.weight(.medium))
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(14)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(Color.indigo.opacity(0.18), lineWidth: 0.8)
                    )
                    .padding(.horizontal, 20)

                    // Live Ecosystem Connectors Card
                    NavigationLink {
                        ConnectorsHubView()
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(
                                        colors: [Color.teal, Color.blue],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ))
                                    .frame(width: 40, height: 40)
                                Image(systemName: "antenna.radiowaves.left.and.right")
                                    .font(.system(size: 18, weight: .semibold))
                                    .foregroundStyle(.white)
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text("Ecosystem Connectors")
                                        .font(.subheadline.weight(.semibold))
                                    Text("LIVE")
                                        .font(.system(size: 9, weight: .bold))
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(Color.green.opacity(0.2), in: Capsule())
                                        .foregroundStyle(Color.green)
                                }
                                Text("Reminders, Alarms, Email, Apple Watch & Notes")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                        .padding(14)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.primary.opacity(0.08), lineWidth: 0.5)
                        )
                        .shadow(color: Color.black.opacity(0.02), radius: 4, y: 2)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 20)

                    // Day-One Life Enrichment Hub
                    NavigationLink {
                        DayOneHubView()
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(
                                        colors: [Color.purple, Color.indigo],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ))
                                    .frame(width: 40, height: 40)
                                Image(systemName: "sparkles.rectangle.stack.fill")
                                    .font(.system(size: 18, weight: .semibold))
                                    .foregroundStyle(.white)
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text("Day-One Ingestion Hub")
                                        .font(.subheadline.weight(.semibold))
                                    Text("INSTANT")
                                        .font(.system(size: 9, weight: .bold))
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(Color.purple.opacity(0.2), in: Capsule())
                                        .foregroundStyle(Color.purple)
                                }
                                Text("Starter Packs, Photos Discovery & Batch Files Import")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                        .padding(14)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.purple.opacity(0.25), lineWidth: 0.8)
                        )
                        .shadow(color: Color.purple.opacity(0.04), radius: 6, y: 2)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 20)

                    // Trip Binders & Travel Hub Card
                    NavigationLink {
                        TravelTripHubView()
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(
                                        colors: [Color.blue, Color.cyan],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ))
                                    .frame(width: 40, height: 40)
                                Image(systemName: "airplane.circle.fill")
                                    .font(.system(size: 20, weight: .semibold))
                                    .foregroundStyle(.white)
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text("Trip Binders & Gate Pass")
                                        .font(.subheadline.weight(.semibold))
                                    Text("TRAVEL")
                                        .font(.system(size: 9, weight: .bold))
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(Color.blue.opacity(0.2), in: Capsule())
                                        .foregroundStyle(Color.blue)
                                }
                                Text("New York Trip (in 3 days) • 4 documents & $1,280 spent")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                        .padding(14)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.blue.opacity(0.25), lineWidth: 0.8)
                        )
                        .shadow(color: Color.blue.opacity(0.04), radius: 6, y: 2)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 20)

                    // Financial Analytics & Expense Hub Card
                    NavigationLink {
                        FinancialAnalyticsHubView()
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(
                                        colors: [Color.green, Color.teal],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ))
                                    .frame(width: 40, height: 40)
                                Image(systemName: "chart.pie.fill")
                                    .font(.system(size: 18, weight: .semibold))
                                    .foregroundStyle(.white)
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text("Finance & Spend Hub")
                                        .font(.subheadline.weight(.semibold))
                                    Text("ANALYTICS")
                                        .font(.system(size: 9, weight: .bold))
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(Color.green.opacity(0.2), in: Capsule())
                                        .foregroundStyle(Color.green)
                                }
                                Text("Monthly spend breakdown, warranties & 1-tap tax export")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                        .padding(14)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.green.opacity(0.25), lineWidth: 0.8)
                        )
                        .shadow(color: Color.green.opacity(0.04), radius: 6, y: 2)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 20)

                    // Health & Medical Subscriptions Hub Card
                    NavigationLink {
                        HealthHubView()
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(
                                        colors: [Color.red, Color.pink],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ))
                                    .frame(width: 40, height: 40)
                                Image(systemName: "heart.text.square.fill")
                                    .font(.system(size: 20, weight: .semibold))
                                    .foregroundStyle(.white)
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text("Health & Medical Hub")
                                        .font(.subheadline.weight(.semibold))
                                    Text("HEALTH")
                                        .font(.system(size: 9, weight: .bold))
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(Color.red.opacity(0.18), in: Capsule())
                                        .foregroundStyle(Color.red)
                                }
                                Text("Apple Health vitals, prescription countdowns & dosage alerts")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                        .padding(14)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.red.opacity(0.22), lineWidth: 0.8)
                        )
                        .shadow(color: Color.red.opacity(0.04), radius: 6, y: 2)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 20)

                    // Amazon & Package Deliveries Hub Card
                    NavigationLink {
                        DeliveriesTrackerHubView()
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(
                                        colors: [Color.orange, Color(red: 0.95, green: 0.5, blue: 0.15)],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ))
                                    .frame(width: 40, height: 40)
                                Image(systemName: "box.truck.fill")
                                    .font(.system(size: 18, weight: .semibold))
                                    .foregroundStyle(.white)
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text("Amazon & Package Deliveries")
                                        .font(.subheadline.weight(.semibold))
                                    Text("ARRIVALS")
                                        .font(.system(size: 9, weight: .bold))
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(Color.orange.opacity(0.2), in: Capsule())
                                        .foregroundStyle(Color.orange)
                                }
                                Text("Live tracking, order arrivals scan & delivery reminders")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                        .padding(14)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.orange.opacity(0.25), lineWidth: 0.8)
                        )
                        .shadow(color: Color.orange.opacity(0.04), radius: 6, y: 2)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 20)

                    // Dynamic Island & Live Activities Control Card
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            HStack(spacing: 6) {
                                Image(systemName: "platter.2.filled.iphone")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(Color.cyan)
                                Text("DYNAMIC ISLAND & LIVE ACTIVITIES")
                                    .font(.system(size: 10, weight: .bold))
                                    .tracking(0.8)
                                    .foregroundStyle(Color.cyan)
                            }
                            Spacer()
                            Text(liveActivities.isFlightActivityActive || liveActivities.isDeliveryActivityActive ? "ACTIVE" : "STANDBY")
                                .font(.system(size: 8, weight: .bold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(
                                    (liveActivities.isFlightActivityActive || liveActivities.isDeliveryActivityActive)
                                        ? Color.green.opacity(0.18)
                                        : Color(.secondarySystemBackground),
                                    in: Capsule()
                                )
                                .foregroundStyle((liveActivities.isFlightActivityActive || liveActivities.isDeliveryActivityActive) ? Color.green : Color.secondary)
                        }

                        Text("Stream your boarding pass gate updates and Amazon package delivery countdown directly to Dynamic Island and Lock Screen.")
                            .font(.caption2)
                            .foregroundStyle(.secondary)

                        HStack(spacing: 8) {
                            Button {
                                if liveActivities.isFlightActivityActive {
                                    liveActivities.stopFlightLiveActivity()
                                } else {
                                    liveActivities.startFlightLiveActivity()
                                }
                            } label: {
                                HStack(spacing: 5) {
                                    Image(systemName: liveActivities.isFlightActivityActive ? "stop.fill" : "airplane.departure")
                                        .font(.caption2.weight(.bold))
                                    Text(liveActivities.isFlightActivityActive ? "Stop Flight Pass" : "Flight Gate Pass")
                                        .font(.caption2.weight(.bold))
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                                .background(
                                    liveActivities.isFlightActivityActive ? Color.blue : Color.blue.opacity(0.12),
                                    in: RoundedRectangle(cornerRadius: 10)
                                )
                                .foregroundStyle(liveActivities.isFlightActivityActive ? Color.white : Color.blue)
                            }
                            .buttonStyle(.plain)

                            Button {
                                if liveActivities.isDeliveryActivityActive {
                                    liveActivities.stopDeliveryLiveActivity()
                                } else {
                                    liveActivities.startDeliveryLiveActivity()
                                }
                            } label: {
                                HStack(spacing: 5) {
                                    Image(systemName: liveActivities.isDeliveryActivityActive ? "stop.fill" : "box.truck.fill")
                                        .font(.caption2.weight(.bold))
                                    Text(liveActivities.isDeliveryActivityActive ? "Stop Delivery" : "Amazon Delivery")
                                        .font(.caption2.weight(.bold))
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                                .background(
                                    liveActivities.isDeliveryActivityActive ? Color.orange : Color.orange.opacity(0.12),
                                    in: RoundedRectangle(cornerRadius: 10)
                                )
                                .foregroundStyle(liveActivities.isDeliveryActivityActive ? Color.white : Color.orange)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(14)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(Color.cyan.opacity(0.2), lineWidth: 0.8)
                    )
                    .padding(.horizontal, 20)

                    // Vault Health & Completeness Audit Card
                    VaultHealthAuditCardView()
                        .padding(.horizontal, 20)

                    // Siri Shortcuts & Action Button Hub Card
                    SiriShortcutsHubCard()
                        .padding(.horizontal, 20)

                    // Hardware Privacy & Security Status Badge
                    HStack(spacing: 12) {
                        Image(systemName: "lock.shield.fill")
                            .font(.title2)
                            .foregroundStyle(Color.green)

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Hardware-Backed On-Device Vault")
                                .font(.footnote.weight(.semibold))
                            Text("SQLCipher AES-256 + Apple Keychain. Zero cloud sync.")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                    .padding(14)
                    .background(Color.green.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.green.opacity(0.2), lineWidth: 0.5)
                    )
                    .padding(.horizontal, 20)

                    // Recent Captures Section
                    if !items.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Text("Recent Captures")
                                    .font(.headline)
                                Spacer()
                                NavigationLink {
                                    VaultListView()
                                } label: {
                                    Text("See all")
                                        .font(.subheadline)
                                        .foregroundStyle(Color.accentColor)
                                }
                            }
                            .padding(.horizontal, 20)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 14) {
                                    ForEach(items.prefix(5), id: \.id) { item in
                                        NavigationLink {
                                            VaultItemDetailView(itemId: item.id)
                                        } label: {
                                            RecentItemCard(item: item)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                                .padding(.horizontal, 20)
                            }
                        }
                    }

                    // Shared Platform Capabilities Demo Group
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Shared Multiplatform Capabilities")
                            .font(.headline)
                            .padding(.horizontal, 20)

                        VStack(spacing: 8) {
                            NavigationLink {
                                HapticsDemoView()
                            } label: {
                                CapabilityRow(title: "Tactile Haptics Engine", icon: "waveform.path", subtitle: "Physics-tuned feedback profiles")
                            }
                            NavigationLink {
                                NotificationsDemoView()
                            } label: {
                                CapabilityRow(title: "Private Notifications", icon: "bell.badge", subtitle: "Private lockscreen alerts")
                            }
                            NavigationLink {
                                AuthDemoView()
                            } label: {
                                CapabilityRow(title: "Biometric & Decoy Auth", icon: "faceid", subtitle: "Face ID and duress isolation")
                            }
                            NavigationLink {
                                ContentView()
                            } label: {
                                CapabilityRow(title: "Bilingual Engine", icon: "globe", subtitle: "Arabic & English dynamic localization")
                            }
                        }
                        .padding(.horizontal, 20)
                    }

                    Spacer(minLength: 40)
                }
                .padding(.bottom, 80)
            }
            .navigationBarHidden(true)
            .overlay(alignment: .bottomTrailing) {
                // Floating Scan / Capture Action Button
                Button {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    showingCaptureSheet = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "doc.viewfinder")
                            .font(.headline)
                        Text("Scan Document")
                            .font(.subheadline.weight(.semibold))
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 14)
                    .background(
                        LinearGradient(
                            colors: [Color.accentColor, Color.accentColor.opacity(0.85)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        in: Capsule()
                    )
                    .foregroundColor(.white)
                    .shadow(color: Color.accentColor.opacity(0.35), radius: 10, y: 5)
                }
                .buttonStyle(.plain)
                .padding(.trailing, 20)
                .padding(.bottom, 24)
                .accessibilityIdentifier("homeFloatingCapture")
            }
            .sheet(isPresented: $showingCaptureSheet) {
                CaptureSheet()
            }
            .onAppear {
                clipboard.checkClipboard()
                bridge.startObservingItems { loaded in
                    items = loaded
                }
                bridge.startObservingCollections { loaded in
                    collections = loaded
                }
                bridge.loadBriefing { snapshot in
                    briefing = snapshot
                    pulse.refresh(
                        calendarEvents: calendar.upcomingEvents,
                        reminders: [],
                        expiringCount: snapshot?.expiringSoon.count ?? 0
                    )
                }
            }
        }
    }

    private func saveClipboardSnippet(_ snippet: ClipboardWatcher.ClipboardSnippet) {
        let title = "Clipboard: \(snippet.displayTitle)"
        let metadataPairs: [(String, String)] = [
            ("Document Type", snippet.category),
            ("Date", Date().formatted(date: .abbreviated, time: .shortened)),
            ("Source", "iPhone Clipboard")
        ]
        var metaDict: [String: String] = [:]
        for (k, v) in metadataPairs { metaDict[k] = v }
        let metaData = (try? JSONSerialization.data(withJSONObject: metaDict, options: [])) ?? Data()
        let metaJson = String(data: metaData, encoding: .utf8) ?? "{}"

        let cardImage = DocumentCardRenderer.renderCard(
            title: title,
            category: snippet.lens,
            metadata: metadataPairs,
            accentColor: snippet.lens == "TRAVEL" ? .systemBlue : .systemOrange
        )

        guard let jpeg = cardImage.jpegData(compressionQuality: 0.85) else { return }
        let tempPath = NSTemporaryDirectory() + "clip-\(UUID().uuidString).jpg"
        FileManager.default.createFile(atPath: tempPath, contents: jpeg)

        bridge.importCapturedDocument(
            tempPath: tempPath,
            sourceName: "MANUAL",
            title: title,
            summary: String(snippet.rawText.prefix(140)),
            rawOcrText: snippet.rawText,
            parsedMetadataJson: metaJson,
            lensTags: [snippet.lens],
            primaryLensId: snippet.lens,
            expiryDateMillis: 0,
            evidenceCoordinatesJson: "{}"
        ) { _ in
            try? FileManager.default.removeItem(atPath: tempPath)
            DispatchQueue.main.async {
                clipboard.dismiss()
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
        }
    }
}

// MARK: - Subviews

private struct DashboardHubCard: View {
    let title: String
    let subtitle: String
    let systemImage: String
    let gradient: [Color]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ZStack {
                Circle()
                    .fill(LinearGradient(colors: gradient, startPoint: .topLeading, endPoint: .bottomTrailing).opacity(0.18))
                    .frame(width: 44, height: 44)
                Image(systemName: systemImage)
                    .font(.title3)
                    .foregroundStyle(LinearGradient(colors: gradient, startPoint: .topLeading, endPoint: .bottomTrailing))
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.primary)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.primary.opacity(0.06), lineWidth: 0.5)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 6, y: 3)
    }
}

private struct RecentItemCard: View {
    let item: VaultItemSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            CaptureThumbnail(path: item.capturedImageUri)
                .frame(width: 140, height: 95)
                .clipped()
                .cornerRadius(12)

            Text(item.title)
                .font(.subheadline.weight(.medium))
                .lineLimit(1)
                .frame(width: 140, alignment: .leading)

            if !item.lensTags.isEmpty {
                Text(item.lensTags.first ?? "")
                    .font(.system(size: 10, weight: .semibold))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.accentColor.opacity(0.12), in: Capsule())
                    .foregroundStyle(Color.accentColor)
            }
        }
        .padding(10)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.primary.opacity(0.06), lineWidth: 0.5)
        )
    }
}

private struct CapabilityRow: View {
    let title: String
    let icon: String
    let subtitle: String

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(Color.accentColor)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.medium))
                Text(subtitle)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.primary.opacity(0.05), lineWidth: 0.5)
        )
    }
}

private struct SettingsDemoView: View {
    var body: some View {
        AboutView()
    }
}

private extension Color {
    static let amberAccent = Color(red: 0.96, green: 0.62, blue: 0.04)
}

struct VaultHealthAuditCardView: View {
    @StateObject private var auditor = VaultHealthAuditor.shared
    @State private var isExpanded: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                HStack(spacing: 8) {
                    Image(systemName: "shield.lefthalf.filled.badge.checkmark")
                        .font(.title3)
                        .foregroundStyle(Color.blue)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Vault Health & Completeness")
                            .font(.subheadline.weight(.bold))
                        Text("Life coverage audit • 5 Core Pillars")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Text("\(auditor.report.scorePercentage)%")
                    .font(.title3.weight(.heavy))
                    .foregroundStyle(Color.blue)
            }

            // Progress Bar
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.blue.opacity(0.15))
                        .frame(height: 8)
                    Capsule()
                        .fill(LinearGradient(colors: [.blue, .teal], startPoint: .leading, endPoint: .trailing))
                        .frame(width: geo.size.width * CGFloat(auditor.report.scorePercentage) / 100.0, height: 8)
                }
            }
            .frame(height: 8)

            if let notice = auditor.report.criticalActionNotice {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .font(.caption)
                        .foregroundStyle(.orange)
                    Text(notice)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            Button {
                withAnimation(.spring(response: 0.3)) {
                    isExpanded.toggle()
                }
            } label: {
                HStack {
                    Text(isExpanded ? "Hide Pillar Breakdown" : "View 5 Pillars Breakdown")
                        .font(.caption.weight(.semibold))
                    Spacer()
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.caption)
                }
                .foregroundStyle(Color.blue)
            }
            .buttonStyle(.plain)

            if isExpanded {
                VStack(spacing: 8) {
                    ForEach(auditor.report.pillars) { p in
                        HStack(spacing: 8) {
                            Image(systemName: p.icon)
                                .font(.caption)
                                .foregroundStyle(p.isComplete ? Color.green : Color.orange)
                                .frame(width: 18)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(p.name)
                                    .font(.caption.weight(.bold))
                                Text(p.advice)
                                    .font(.system(size: 10))
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: p.isComplete ? "checkmark.circle.fill" : "circle.dashed")
                                .font(.caption)
                                .foregroundStyle(p.isComplete ? Color.green : Color.orange)
                        }
                        .padding(8)
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 10))
                    }
                }
                .transition(.opacity)
            }
        }
        .padding(14)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(Color.blue.opacity(0.2), lineWidth: 0.8)
        )
    }
}

#Preview {
    HomeView()
}
